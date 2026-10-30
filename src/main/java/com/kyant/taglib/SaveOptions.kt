package com.kyant.taglib

/**
 * Configuration options for saving metadata and pictures to audio files.
 *
 * @param mode Which metadata tag formats to write (and whether to strip other tags)
 * @param id3v2Version ID3v2 version to use when writing ID3v2 tags
 */
public data class SaveOptions @JvmOverloads public constructor(public val mode: TagSaveMode = TagSaveMode.AllTags, public val id3v2Version: Id3v2Version = Id3v2Version.V2_4) {
    public companion object {
        /**
         * Default save options preserving TagLib's standard save behaviour.
         */
        @JvmField
        public val Default: SaveOptions = SaveOptions()
    }
}
