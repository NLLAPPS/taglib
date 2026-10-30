import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.kyant.taglib.Id3v2Version
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.SaveOptions
import com.kyant.taglib.TagLib
import com.kyant.taglib.TagSaveMode
import org.junit.Assert
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset

class Tests {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun test_all() {
        read_and_write_m4a()
        read_and_write_pictures_flac()
        read_flac_multiple_pictures()
        ensure_utf8()
        bad_encoding()
    }

    @Test
    fun default_mp3_save_is_backward_compatible() {
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            val props = hashMapOf(
                "TITLE" to arrayOf("Default Title"),
                "ARTIST" to arrayOf("Default Artist"),
            )
            Assert.assertTrue(TagLib.savePropertyMap(fd.dup().detachFd(), props))

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v1)
            Assert.assertTrue(info.hasId3v2)
            Assert.assertFalse(info.hasApe)
            Assert.assertEquals(4, info.id3v2MajorVersion)
            Assert.assertEquals("Default Title", info.id3v1Title)
            Assert.assertEquals("Default Title", info.id3v2Title)

            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Default Title", metadata.propertyMap["TITLE"]!!.single())
            Assert.assertEquals("Default Artist", metadata.propertyMap["ARTIST"]!!.single())
        }
    }

    @Test
    fun id3v2_only_does_not_create_id3v1_and_preserves_other_existing_tags() {
        // 1. Tagless MP3 -> ID3v2-only does not create ID3v1
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            val props = hashMapOf(
                "TITLE" to arrayOf("V2 Only Title"),
                "ARTIST" to arrayOf("V2 Only Artist"),
            )
            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                props,
                SaveOptions(mode = TagSaveMode.Id3v2Only),
            )
            Assert.assertTrue(saved)

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v2)
            Assert.assertFalse(info.hasId3v1)
            Assert.assertFalse(info.hasApe)
            Assert.assertEquals(4, info.id3v2MajorVersion)
            Assert.assertEquals("V2 Only Title", info.id3v2Title)
        }

        // 2. MP3 with existing ID3v1 and APE -> ID3v2-only (without strip) preserves both
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Legacy V1")),
                    SaveOptions(mode = TagSaveMode.Id3v1Only),
                )
            )
            Assert.assertTrue(TagLib.setMpegApeTitle(fd.dup().detachFd(), "Legacy APE"))

            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                hashMapOf("TITLE" to arrayOf("Updated V2")),
                SaveOptions(mode = TagSaveMode.Id3v2Only),
            )
            Assert.assertTrue(saved)

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v2)
            Assert.assertTrue(info.hasId3v1)
            Assert.assertTrue(info.hasApe)
            Assert.assertEquals("Updated V2", info.id3v2Title)
            Assert.assertEquals("Legacy V1", info.id3v1Title)
            Assert.assertEquals("Legacy APE", info.apeTitle)
        }
    }

    @Test
    fun id3v1_only_does_not_create_id3v2_and_preserves_other_existing_tags() {
        // 1. Tagless MP3 -> ID3v1-only does not create ID3v2
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            val props = hashMapOf(
                "TITLE" to arrayOf("V1 Only Title"),
                "ARTIST" to arrayOf("V1 Only Artist"),
            )
            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                props,
                SaveOptions(mode = TagSaveMode.Id3v1Only),
            )
            Assert.assertTrue(saved)

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v1)
            Assert.assertFalse(info.hasId3v2)
            Assert.assertFalse(info.hasApe)
            Assert.assertEquals("V1 Only Title", info.id3v1Title)

            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("V1 Only Title", metadata.propertyMap["TITLE"]!!.single())
            Assert.assertEquals("V1 Only Artist", metadata.propertyMap["ARTIST"]!!.single())
        }

        // 2. MP3 with existing ID3v2 and APE -> ID3v1-only (without strip) preserves both
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Existing V2")),
                    SaveOptions(mode = TagSaveMode.Id3v2Only),
                )
            )
            Assert.assertTrue(TagLib.setMpegApeTitle(fd.dup().detachFd(), "Existing APE"))

            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                hashMapOf("TITLE" to arrayOf("Updated V1")),
                SaveOptions(mode = TagSaveMode.Id3v1Only),
            )
            Assert.assertTrue(saved)

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v1)
            Assert.assertTrue(info.hasId3v2)
            Assert.assertTrue(info.hasApe)
            Assert.assertEquals("Updated V1", info.id3v1Title)
            Assert.assertEquals("Existing V2", info.id3v2Title)
            Assert.assertEquals("Existing APE", info.apeTitle)
        }
    }

    @Test
    fun strip_modes_remove_unwanted_tags() {
        // 1. ID3v2OnlyStripOthers removes existing ID3v1 and APE tags
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Initial Title")),
                    SaveOptions(mode = TagSaveMode.AllTags),
                )
            )
            Assert.assertTrue(TagLib.setMpegApeTitle(fd.dup().detachFd(), "Initial APE"))

            val before = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(before.hasId3v1)
            Assert.assertTrue(before.hasId3v2)
            Assert.assertTrue(before.hasApe)

            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                hashMapOf("TITLE" to arrayOf("Clean V2")),
                SaveOptions(
                    mode = TagSaveMode.Id3v2OnlyStripOthers,
                    id3v2Version = Id3v2Version.V2_3,
                ),
            )
            Assert.assertTrue(saved)

            val after = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(after.hasId3v2)
            Assert.assertFalse(after.hasId3v1)
            Assert.assertFalse(after.hasApe)
            Assert.assertEquals(3, after.id3v2MajorVersion)
            Assert.assertEquals("Clean V2", after.id3v2Title)
        }

        // 2. ID3v1OnlyStripOthers removes existing ID3v2 and APE tags
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Initial Title")),
                    SaveOptions(mode = TagSaveMode.AllTags),
                )
            )
            Assert.assertTrue(TagLib.setMpegApeTitle(fd.dup().detachFd(), "Initial APE"))

            val saved = TagLib.savePropertyMap(
                fd.dup().detachFd(),
                hashMapOf("TITLE" to arrayOf("Clean V1")),
                SaveOptions(mode = TagSaveMode.Id3v1OnlyStripOthers),
            )
            Assert.assertTrue(saved)

            val after = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(after.hasId3v1)
            Assert.assertFalse(after.hasId3v2)
            Assert.assertFalse(after.hasApe)
            Assert.assertEquals("Clean V1", after.id3v1Title)

            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Clean V1", metadata.propertyMap["TITLE"]!!.single())
        }
    }

    @Test
    fun id3v2_version_selection_writes_v23_and_v24() {
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            // bladeenc.mp3 initially has ID3v2.3
            val initialInfo = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertEquals(3, initialInfo.id3v2MajorVersion)

            val props = hashMapOf(
                "TITLE" to arrayOf("Versioned Title ú"),
                "ARTIST" to arrayOf("Versioned Artist"),
                "DATE" to arrayOf("2025"),
            )

            // Upgrade to ID3v2.4 in Id3v2OnlyStripOthers
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    props,
                    SaveOptions(
                        mode = TagSaveMode.Id3v2OnlyStripOthers,
                        id3v2Version = Id3v2Version.V2_4,
                    ),
                )
            )
            val v24Info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertEquals(4, v24Info.id3v2MajorVersion)
            Assert.assertFalse(v24Info.hasId3v1)
            Assert.assertEquals(
                "Versioned Title ú",
                TagLib.getMetadata(fd.dup().detachFd())!!.propertyMap["TITLE"]!!.single()
            )

            // Downgrade to ID3v2.3 in Id3v2Only
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    props,
                    SaveOptions(
                        mode = TagSaveMode.Id3v2Only,
                        id3v2Version = Id3v2Version.V2_3,
                    ),
                )
            )
            val v23Info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertEquals(3, v23Info.id3v2MajorVersion)
            Assert.assertFalse(v23Info.hasId3v1)
            Assert.assertEquals(
                "Versioned Title ú",
                TagLib.getMetadata(fd.dup().detachFd())!!.propertyMap["TITLE"]!!.single()
            )

            // AllTags with ID3v2.3 writes both ID3v1 and ID3v2.3
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    props,
                    SaveOptions(
                        mode = TagSaveMode.AllTags,
                        id3v2Version = Id3v2Version.V2_3,
                    ),
                )
            )
            val allTagsV23Info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(allTagsV23Info.hasId3v1)
            Assert.assertTrue(allTagsV23Info.hasId3v2)
            Assert.assertEquals(3, allTagsV23Info.id3v2MajorVersion)
        }
    }

    @Test
    fun picture_and_property_saves_respect_same_configuration_and_preserve_data() {
        val cover1 = loadSamplePicture("699_200x200.jpg", "Front Cover")
        val cover2 = loadSamplePicture("947_200x200.jpg", "Back Cover")

        // 1. Property save followed by picture save in ID3v2.3-only mode preserves properties & avoids ID3v1
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)

            val optionsV23Only = SaveOptions(
                mode = TagSaveMode.Id3v2Only,
                id3v2Version = Id3v2Version.V2_3,
            )
            val props = hashMapOf(
                "TITLE" to arrayOf("Artwork Song"),
                "ARTIST" to arrayOf("Artwork Artist"),
                "CUSTOM_PROP" to arrayOf("Custom Value"),
            )
            Assert.assertTrue(TagLib.savePropertyMap(fd.dup().detachFd(), props, optionsV23Only))
            Assert.assertTrue(TagLib.savePictures(fd.dup().detachFd(), arrayOf(cover1), optionsV23Only))

            val infoAfterPic = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(infoAfterPic.hasId3v2)
            Assert.assertFalse(infoAfterPic.hasId3v1)
            Assert.assertEquals(3, infoAfterPic.id3v2MajorVersion)

            val metadataAfterPic = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Artwork Song", metadataAfterPic.propertyMap["TITLE"]!!.single())
            Assert.assertEquals("Artwork Artist", metadataAfterPic.propertyMap["ARTIST"]!!.single())
            Assert.assertEquals("Custom Value", metadataAfterPic.propertyMap["CUSTOM_PROP"]!!.single())
            Assert.assertEquals(cover1, metadataAfterPic.pictures.single())

            // 2. Updating properties afterwards preserves existing artwork and updates ID3v2 version to 2.4
            val optionsV24Only = SaveOptions(
                mode = TagSaveMode.Id3v2Only,
                id3v2Version = Id3v2Version.V2_4,
            )
            val updatedProps = HashMap(metadataAfterPic.propertyMap).apply {
                this["TITLE"] = arrayOf("Artwork Song Remastered")
            }
            Assert.assertTrue(TagLib.savePropertyMap(fd.dup().detachFd(), updatedProps, optionsV24Only))

            val infoAfterProp = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(infoAfterProp.hasId3v2)
            Assert.assertFalse(infoAfterProp.hasId3v1)
            Assert.assertEquals(4, infoAfterProp.id3v2MajorVersion)

            val metadataAfterProp = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Artwork Song Remastered", metadataAfterProp.propertyMap["TITLE"]!!.single())
            Assert.assertEquals("Custom Value", metadataAfterProp.propertyMap["CUSTOM_PROP"]!!.single())
            Assert.assertEquals(cover1, metadataAfterProp.pictures.single())
        }

        // 3. savePictures with Id3v2OnlyStripOthers strips ID3v1 and APE while preserving ID3v2 properties
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Keep Me")),
                    SaveOptions(mode = TagSaveMode.AllTags),
                )
            )
            Assert.assertTrue(TagLib.setMpegApeTitle(fd.dup().detachFd(), "Strip Me APE"))

            Assert.assertTrue(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(cover2),
                    SaveOptions(
                        mode = TagSaveMode.Id3v2OnlyStripOthers,
                        id3v2Version = Id3v2Version.V2_3,
                    ),
                )
            )

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v2)
            Assert.assertFalse(info.hasId3v1)
            Assert.assertFalse(info.hasApe)
            Assert.assertEquals(3, info.id3v2MajorVersion)

            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Keep Me", metadata.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(cover2, metadata.pictures.single())
        }

        // 4. savePictures with ID3v1-only modes fails predictably without creating ID3v2 or stripping tags
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->
            clearAllMp3Tags(fd)
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    hashMapOf("TITLE" to arrayOf("Only V1")),
                    SaveOptions(mode = TagSaveMode.Id3v1Only),
                )
            )

            Assert.assertFalse(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(cover1),
                    SaveOptions(mode = TagSaveMode.Id3v1Only),
                )
            )
            Assert.assertFalse(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(cover1),
                    SaveOptions(mode = TagSaveMode.Id3v1OnlyStripOthers),
                )
            )

            val info = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
            Assert.assertTrue(info.hasId3v1)
            Assert.assertFalse(info.hasId3v2)
            Assert.assertEquals("Only V1", info.id3v1Title)

            // 5. Adding pictures in Id3v2Only mode to a file that only had ID3v1 preserves existing metadata
            Assert.assertTrue(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(cover1),
                    SaveOptions(mode = TagSaveMode.Id3v2Only),
                )
            )
            val upgradedMetadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Only V1", upgradedMetadata.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(cover1, upgradedMetadata.pictures.single())
        }
    }

    @Test
    fun other_audio_formats_retain_behaviour_and_reject_unsupported_modes() {
        val samplePic = loadSamplePicture("699_200x200.jpg", "Front Cover")

        // M4A
        getFdFromAssets(context, "Sample_BeeMoved_48kHz16bit.m4a").use { fd ->
            val original = TagLib.getMetadata(fd.dup().detachFd())!!

            // Unsupported MPEG-only modes fail without modifying the M4A file
            val badProps = HashMap(original.propertyMap).apply {
                this["TITLE"] = arrayOf("Should Not Be Written")
            }
            Assert.assertFalse(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    badProps,
                    SaveOptions(mode = TagSaveMode.Id3v2Only),
                )
            )
            Assert.assertFalse(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    badProps,
                    SaveOptions(mode = TagSaveMode.Id3v1Only),
                )
            )
            Assert.assertFalse(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(samplePic),
                    SaveOptions(mode = TagSaveMode.Id3v2OnlyStripOthers),
                )
            )

            val unchanged = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Bee Moved", unchanged.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(58336, unchanged.pictures.single().data.size)

            // AllTags continues working normally
            val updatedProps = HashMap(original.propertyMap).apply {
                this["TITLE"] = arrayOf("Bee Moved Updated")
            }
            Assert.assertTrue(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    updatedProps,
                    SaveOptions(mode = TagSaveMode.AllTags, id3v2Version = Id3v2Version.V2_3),
                )
            )
            val updated = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Bee Moved Updated", updated.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(58336, updated.pictures.single().data.size)
        }

        // FLAC
        getFdFromAssets(context, "是什么让我遇见这样的你 - 白安.flac").use { fd ->
            val original = TagLib.getMetadata(fd.dup().detachFd())!!

            val badProps = HashMap(original.propertyMap).apply {
                this["TITLE"] = arrayOf("Should Not Be Written")
            }
            Assert.assertFalse(
                TagLib.savePropertyMap(
                    fd.dup().detachFd(),
                    badProps,
                    SaveOptions(mode = TagSaveMode.Id3v1OnlyStripOthers),
                )
            )
            Assert.assertFalse(
                TagLib.savePictures(
                    fd.dup().detachFd(),
                    arrayOf(samplePic),
                    SaveOptions(mode = TagSaveMode.Id3v2Only),
                )
            )

            val unchanged = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("是什么让我遇见这样的你", unchanged.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(42716, unchanged.pictures.single().data.size)
        }
    }

    private fun read_and_write_m4a() {
        getFdFromAssets(context, "Sample_BeeMoved_48kHz16bit.m4a").use { fd ->

            // Read audio properties

            val audioProperties = TagLib.getAudioProperties(fd.dup().detachFd())!!
            Assert.assertEquals(39936, audioProperties.length)

            // Read metadata

            val metadata = TagLib.getMetadata(fd = fd.dup().detachFd(), readPictures = true)!!
            Assert.assertEquals("Bee Moved", metadata.propertyMap["TITLE"]!!.single())
            Assert.assertEquals(58336, metadata.pictures.single().data.size)

            // Read single metadata

            val artists = TagLib.getMetadataPropertyValues(fd.dup().detachFd(), "ARTIST")!!
            Assert.assertEquals("Blue Monday FM", artists.single())

            // Save metadata

            val newTitle = "Bee Moved (Remix)"
            val newPropertyMap = metadata.propertyMap.apply {
                this["TITLE"] = arrayOf(newTitle)
            }
            val saved = TagLib.savePropertyMap(fd.dup().detachFd(), newPropertyMap)
            Assert.assertTrue(saved)

            val newMetadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals(newTitle, newMetadata.propertyMap["TITLE"]!!.single())
        }
    }

    private fun read_and_write_pictures_flac() {
        getFdFromAssets(context, "是什么让我遇见这样的你 - 白安.flac").use { fd ->
            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("是什么让我遇见这样的你", metadata.propertyMap["TITLE"]!!.single())

            val pictures = TagLib.getPictures(fd.dup().detachFd())
            val originalPicture = pictures.single()
            Assert.assertEquals(42716, originalPicture.data.size)

            // Save the original picture

            val saved = TagLib.savePictures(fd.dup().detachFd(), arrayOf(originalPicture))
            Assert.assertTrue(saved)
            Assert.assertEquals(originalPicture, TagLib.getPictures(fd.dup().detachFd()).single())

            // Save new multiple pictures

            val newPicture1 = loadSamplePicture("699_200x200.jpg", "Front Cover")
            val newPicture2 = loadSamplePicture("947_200x200.jpg", "Back Cover")

            TagLib.savePictures(fd.dup().detachFd(), arrayOf(newPicture1, newPicture2))
            val newPictures = TagLib.getPictures(fd.dup().detachFd())
            Assert.assertEquals(2, newPictures.size)
            Assert.assertEquals(newPicture1, newPictures[0])
            Assert.assertEquals(newPicture2, newPictures[1])
        }
    }

    private fun read_flac_multiple_pictures() {
        getFdFromAssets(context, "multiple_album_art.flac").use { fd ->
            val pictures = TagLib.getPictures(fd.dup().detachFd())
            Assert.assertEquals(3, pictures.size)
            Assert.assertEquals(29766, pictures[2].data.size)
        }
    }

    private fun ensure_utf8() {
        getFdFromAssets(context, "bladeenc.mp3").use { fd ->

            // Read metadata

            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals("Test", metadata.propertyMap["TITLE"]!!.single())

            val pictures = TagLib.getPictures(fd.dup().detachFd())
            Assert.assertEquals(0, pictures.size)

            // Save metadata

            val newTitleKey = "TITLE ú"
            val newTitles = arrayOf("Test ú", "Test")
            val newPropertyMap =
                metadata.propertyMap.apply {
                    this[newTitleKey] = newTitles
                }
            val saved = TagLib.savePropertyMap(fd.dup().detachFd(), newPropertyMap)
            Assert.assertTrue(saved)

            val newMetadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertArrayEquals(newTitles, newMetadata.propertyMap[newTitleKey])
        }
    }

    private fun bad_encoding() {
        getFdFromAssets(context, "Honor.mp3").use { fd ->
            val metadata = TagLib.getMetadata(fd.dup().detachFd())!!
            Assert.assertEquals(
                "荣耀".toByteArray(Charset.forName("GB2312")).toString(Charsets.ISO_8859_1),
                metadata.propertyMap["TITLE"]!!.single()
            )
        }
    }

    private fun clearAllMp3Tags(fd: ParcelFileDescriptor) {
        Assert.assertTrue(TagLib.savePictures(fd.dup().detachFd(), emptyArray()))
        Assert.assertTrue(TagLib.savePropertyMap(fd.dup().detachFd(), PropertyMap()))
        val clearedInfo = TagLib.getMpegTagInfo(fd.dup().detachFd())!!
        Assert.assertFalse(clearedInfo.hasId3v1)
        Assert.assertFalse(clearedInfo.hasId3v2)
        Assert.assertFalse(clearedInfo.hasApe)
    }

    private fun loadSamplePicture(assetName: String, pictureType: String): Picture {
        return ByteArrayOutputStream().use {
            BitmapFactory
                .decodeStream(context.assets.open(assetName))
                .compress(Bitmap.CompressFormat.JPEG, 100, it)
            Picture(
                data = it.toByteArray(),
                description = pictureType,
                pictureType = pictureType,
                mimeType = "image/jpeg",
            )
        }
    }

    private fun getFdFromAssets(context: Context, fileName: String): ParcelFileDescriptor {
        val file = getFileFromAssets(context, fileName)
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
    }

    private fun getFileFromAssets(context: Context, fileName: String): File {
        return File(context.cacheDir, fileName).apply {
            outputStream().use { cache ->
                context.assets.open(fileName).use { inputStream ->
                    inputStream.copyTo(cache)
                }
            }
        }
    }
}
