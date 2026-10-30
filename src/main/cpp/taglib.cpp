#include "tfilestream.h"
#include "utils.h"

extern "C" {
JNIEXPORT jobject JNICALL
Java_com_kyant_taglib_TagLib_getAudioProperties(
        JNIEnv *env,
        jclass,
        jint fd,
        jint read_style
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return nullptr;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
    const auto style = static_cast<TagLib::AudioProperties::ReadStyle>(read_style);
    const TagLibExt::FileRef f(path, stream.get(), true, style);

    if (f.isNull()) {
        free(path);
        return nullptr;
    }

    jobject audioProperties = getAudioProperties(env, f);
    free(path);
    return audioProperties;
}

JNIEXPORT jobject JNICALL
Java_com_kyant_taglib_TagLib_getMetadata(
        JNIEnv *env,
        jclass,
        jint fd,
        jboolean read_pictures
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return nullptr;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
    const TagLibExt::FileRef f(path, stream.get(), false);

    if (f.isNull()) {
        free(path);
        return nullptr;
    }

    jobject propertiesMap = getPropertyMap(env, f);
    jobjectArray pictures;
    if (read_pictures) {
        pictures = getPictures(env, f);
    } else {
        pictures = emptyPictureArray(env);
    }

    jobject metadata = env->NewObject(
            metadataClass, metadataConstructor,
            propertiesMap, pictures
    );
    free(path);
    return metadata;
}

JNIEXPORT jobjectArray JNICALL
 Java_com_kyant_taglib_TagLib_getMetadataPropertyValues(
         JNIEnv *env,
         jclass,
         jint fd,
         jstring property_name
 ) {
     const char *propertyName = env->GetStringUTFChars(property_name, nullptr);
     if (propertyName == nullptr) {
         return nullptr;
     }
 
     char *path = getRealPathFromFd(fd);
     if (path == nullptr) {
         env->ReleaseStringUTFChars(property_name, propertyName);
         return nullptr;
     }
     const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
     const TagLibExt::FileRef f(path, stream.get(), false);
 
     if (f.isNull()) {
         free(path);
         return nullptr;
     }
 
     const auto propertyMap = f.properties();
     const auto valueList = propertyMap.find(TagLib::String(propertyName))->second;
     jobjectArray result = env->NewObjectArray(static_cast<jsize>(valueList.size()),
                                               stringClass, nullptr);
     int i = 0;
     for (const auto &value: valueList) {
         jstring jValue = env->NewStringUTF(value.toCString(true));
         env->SetObjectArrayElement(result, i, jValue);
         env->DeleteLocalRef(jValue);
         i++;
     }
 
     env->ReleaseStringUTFChars(property_name, propertyName);
     free(path);
     return result;
 }

JNIEXPORT jobjectArray JNICALL
Java_com_kyant_taglib_TagLib_getPictures(
        JNIEnv *env,
        jclass,
        jint fd
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return nullptr;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
    const TagLibExt::FileRef f(path, stream.get(), false);

    if (f.isNull()) {
        free(path);
        return emptyPictureArray(env);
    }

    jobjectArray pictures = getPictures(env, f);
    free(path);
    return pictures;
}

JNIEXPORT jboolean JNICALL
Java_com_kyant_taglib_TagLib_savePropertyMap(
        JNIEnv *env,
        jclass,
        jint fd,
        jobject property_map,
        jint save_mode,
        jint id3v2_version
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return false;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, false);

    TagSaveMode mode;
    TagLib::ID3v2::Version version;
    if (!parseSaveOptions(save_mode, id3v2_version, mode, version)) {
        free(path);
        return false;
    }

    TagLibExt::FileRef f(path, stream.get(), false);
    if (!canApplySaveOptions(f, mode, false)) {
        free(path);
        return false;
    }

    auto *mpegFile = dynamic_cast<TagLib::MPEG::File *>(f.file());
    const bool hadMpegId3v2 = mpegFile != nullptr && mpegFile->hasID3v2Tag();

    const PropertyMap propertyMap = JniHashMapToPropertyMap(env, property_map);
    if (mpegFile != nullptr) {
        switch (mode) {
            case TagSaveMode::AllTags:
                f.setProperties(propertyMap);
                break;
            case TagSaveMode::Id3v2Only:
            case TagSaveMode::Id3v2OnlyStripOthers:
                mpegFile->ID3v2Tag(true)->setProperties(propertyMap);
                break;
            case TagSaveMode::Id3v1Only:
            case TagSaveMode::Id3v1OnlyStripOthers:
                mpegFile->ID3v1Tag(true)->setProperties(propertyMap);
                break;
        }
    } else {
        f.setProperties(propertyMap);
    }

    const bool success = saveFileWithOptions(f, mode, version, hadMpegId3v2, false);
    free(path);
    return success;
}

JNIEXPORT jboolean JNICALL
Java_com_kyant_taglib_TagLib_savePictures(
        JNIEnv *env,
        jclass,
        jint fd,
        jobjectArray pictures,
        jint save_mode,
        jint id3v2_version
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return false;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, false);

    TagSaveMode mode;
    TagLib::ID3v2::Version version;
    if (!parseSaveOptions(save_mode, id3v2_version, mode, version)) {
        free(path);
        return false;
    }

    TagLibExt::FileRef f(path, stream.get(), false);
    if (!canApplySaveOptions(f, mode, true)) {
        free(path);
        return false;
    }

    auto *mpegFile = dynamic_cast<TagLib::MPEG::File *>(f.file());
    const bool hadMpegId3v2 = mpegFile != nullptr && mpegFile->hasID3v2Tag();

    auto pictureList = JniPictureArrayToPictureList(env, pictures);
    bool updatedPictures;
    if (mpegFile != nullptr &&
        (mode == TagSaveMode::Id3v2Only || mode == TagSaveMode::Id3v2OnlyStripOthers)) {
        updatedPictures = mpegFile->ID3v2Tag(true)->setComplexProperties("PICTURE", pictureList);
    } else {
        updatedPictures = f.setComplexProperties("PICTURE", pictureList);
    }

    if (!updatedPictures) {
        free(path);
        return false;
    }

    const bool success = saveFileWithOptions(f, mode, version, hadMpegId3v2, true);
    free(path);
    return success;
}

JNIEXPORT jobject JNICALL
Java_com_kyant_taglib_TagLib_nativeGetMpegTagInfo(
        JNIEnv *env,
        jclass,
        jint fd
) {
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return nullptr;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, true);
    TagLibExt::FileRef f(path, stream.get(), false);

    if (f.isNull()) {
        free(path);
        return nullptr;
    }

    auto *mpegFile = dynamic_cast<TagLib::MPEG::File *>(f.file());
    if (mpegFile == nullptr) {
        free(path);
        return nullptr;
    }

    jclass infoClass = env->FindClass("com/kyant/taglib/MpegTagInfo");
    if (infoClass == nullptr) {
        free(path);
        return nullptr;
    }
    jmethodID constructor = env->GetMethodID(
            infoClass,
            "<init>",
            "(ZZZILjava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    if (constructor == nullptr) {
        env->DeleteLocalRef(infoClass);
        free(path);
        return nullptr;
    }

    const bool hasId3v1 = mpegFile->hasID3v1Tag();
    const bool hasId3v2 = mpegFile->hasID3v2Tag();
    const bool hasApe = mpegFile->hasAPETag();
    const jint id3v2MajorVersion = (hasId3v2 && mpegFile->ID3v2Tag() != nullptr)
            ? static_cast<jint>(mpegFile->ID3v2Tag()->header()->majorVersion())
            : 0;

    jstring jId3v1Title = (hasId3v1 && mpegFile->ID3v1Tag() != nullptr)
            ? env->NewStringUTF(mpegFile->ID3v1Tag()->title().toCString(true))
            : nullptr;
    jstring jId3v2Title = (hasId3v2 && mpegFile->ID3v2Tag() != nullptr)
            ? env->NewStringUTF(mpegFile->ID3v2Tag()->title().toCString(true))
            : nullptr;
    jstring jApeTitle = (hasApe && mpegFile->APETag() != nullptr)
            ? env->NewStringUTF(mpegFile->APETag()->title().toCString(true))
            : nullptr;

    jobject result = env->NewObject(
            infoClass,
            constructor,
            static_cast<jboolean>(hasId3v1),
            static_cast<jboolean>(hasId3v2),
            static_cast<jboolean>(hasApe),
            id3v2MajorVersion,
            jId3v1Title,
            jId3v2Title,
            jApeTitle
    );

    if (jId3v1Title != nullptr) env->DeleteLocalRef(jId3v1Title);
    if (jId3v2Title != nullptr) env->DeleteLocalRef(jId3v2Title);
    if (jApeTitle != nullptr) env->DeleteLocalRef(jApeTitle);
    env->DeleteLocalRef(infoClass);
    free(path);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_kyant_taglib_TagLib_nativeSetMpegApeTitle(
        JNIEnv *env,
        jclass,
        jint fd,
        jstring title
) {
    if (title == nullptr) {
        return false;
    }
    char *path = getRealPathFromFd(fd);
    if (path == nullptr) {
        return false;
    }
    const auto stream = std::make_unique<TagLib::FileStream>(fd, false);
    TagLibExt::FileRef f(path, stream.get(), false);

    if (f.isNull()) {
        free(path);
        return false;
    }

    auto *mpegFile = dynamic_cast<TagLib::MPEG::File *>(f.file());
    if (mpegFile == nullptr) {
        free(path);
        return false;
    }

    const char *titleChars = env->GetStringUTFChars(title, nullptr);
    if (titleChars == nullptr) {
        free(path);
        return false;
    }
    auto *apeTag = mpegFile->APETag(true);
    if (apeTag == nullptr) {
        env->ReleaseStringUTFChars(title, titleChars);
        free(path);
        return false;
    }
    apeTag->setTitle(TagLib::String(titleChars, TagLib::String::UTF8));
    env->ReleaseStringUTFChars(title, titleChars);

    const bool success = mpegFile->save(
            TagLib::MPEG::File::APE,
            TagLib::File::StripNone,
            TagLib::ID3v2::v4,
            TagLib::File::DoNotDuplicate
    );
    free(path);
    return success;
}
}
