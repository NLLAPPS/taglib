package com.kyant.taglib

/**
 * Controls which metadata tag formats are written when saving audio files.
 */
public enum class TagSaveMode(internal val nativeValue: Int) {
    /**
     * Write all applicable tag formats, preserving TagLib's default save behaviour.
     * For MP3 files, this writes both ID3v1 and ID3v2 tags.
     */
    AllTags(0),

    /**
     * Write ID3v2 tags only without writing ID3v1 or removing other existing tags.
     */
    Id3v2Only(1),

    /**
     * Write ID3v1 tags only without writing ID3v2 or removing other existing tags.
     */
    Id3v1Only(2),

    /**
     * Write ID3v2 tags only and remove existing ID3v1 and APE tags.
     */
    Id3v2OnlyStripOthers(3),

    /**
     * Write ID3v1 tags only and remove existing ID3v2 and APE tags.
     */
    Id3v1OnlyStripOthers(4),
}
