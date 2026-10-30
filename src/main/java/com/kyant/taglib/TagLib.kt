package com.kyant.taglib

/**
 * An object that provides access to the native TagLib library.
 */
public object TagLib {
    @JvmStatic
    private external fun getAudioProperties(
        fd: Int,
        readStyle: Int,
    ): AudioProperties?

    /**
     * Get audio properties from file descriptor.
     *
     * @param fd File descriptor
     * @param readStyle Read style for audio properties to balance speed and accuracy
     */
    @JvmStatic
    public fun getAudioProperties(
        fd: Int,
        readStyle: AudioPropertiesReadStyle = AudioPropertiesReadStyle.Average,
    ): AudioProperties? = getAudioProperties(fd, readStyle.ordinal)

    /**
     * Get metadata from file descriptor.
     *
     * @param fd File descriptor
     * @param readPictures Whether to read pictures
     */
    @JvmStatic
    public external fun getMetadata(
        fd: Int,
        readPictures: Boolean = true,
    ): Metadata?

    /**
     * Get metadata property values from file descriptor.
     *
     * @param fd File descriptor
     * @param propertyName Property name
     */
    @JvmStatic
    public external fun getMetadataPropertyValues(
        fd: Int,
        propertyName: String,
    ): Array<String>?

    /**
     * Get pictures from file descriptor. There may be multiple pictures with different types.
     */
    @JvmStatic
    public external fun getPictures(fd: Int): Array<Picture>

    /**
     * Get front cover from file descriptor.
     */
    @JvmStatic
    public fun getFrontCover(fd: Int): Picture? {
        val pictures = getPictures(fd)
        return pictures.find { picture -> picture.pictureType == "Front Cover" }
            ?: pictures.firstOrNull()
    }

    @JvmStatic
    private external fun savePropertyMap(
        fd: Int,
        propertyMap: PropertyMap,
        saveMode: Int,
        id3v2Version: Int,
    ): Boolean

    /**
     * Save metadata by file descriptor.
     *
     * @param fd File descriptor
     * @param propertyMap Property map to save
     * @param saveOptions Options controlling which tag formats and ID3v2 version are written
     *
     * @return Whether the operation was successful
     */
    @JvmStatic
    @JvmOverloads
    public fun savePropertyMap(
        fd: Int,
        propertyMap: PropertyMap,
        saveOptions: SaveOptions = SaveOptions.Default,
    ): Boolean = savePropertyMap(
        fd = fd,
        propertyMap = propertyMap,
        saveMode = saveOptions.mode.nativeValue,
        id3v2Version = saveOptions.id3v2Version.nativeValue,
    )

    @JvmStatic
    private external fun savePictures(
        fd: Int,
        pictures: Array<Picture>,
        saveMode: Int,
        id3v2Version: Int,
    ): Boolean

    /**
     * Save pictures by file descriptor.
     *
     * @param fd File descriptor
     * @param pictures Pictures to save
     * @param saveOptions Options controlling which tag formats and ID3v2 version are written
     *
     * @return Whether the operation was successful
     */
    @JvmStatic
    @JvmOverloads
    public fun savePictures(
        fd: Int,
        pictures: Array<Picture>,
        saveOptions: SaveOptions = SaveOptions.Default,
    ): Boolean = savePictures(
        fd = fd,
        pictures = pictures,
        saveMode = saveOptions.mode.nativeValue,
        id3v2Version = saveOptions.id3v2Version.nativeValue,
    )

    @JvmStatic
    private external fun nativeGetMpegTagInfo(fd: Int): MpegTagInfo?

    internal fun getMpegTagInfo(fd: Int): MpegTagInfo? = nativeGetMpegTagInfo(fd)

    @JvmStatic
    private external fun nativeSetMpegApeTitle(
        fd: Int,
        title: String,
    ): Boolean

    internal fun setMpegApeTitle(
        fd: Int,
        title: String,
    ): Boolean = nativeSetMpegApeTitle(fd, title)

    init {
        System.loadLibrary("taglib")
    }
}
