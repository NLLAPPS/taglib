package com.kyant.taglib

/**
 * Specifies the ID3v2 standard version to use when writing ID3v2 tags.
 */
public enum class Id3v2Version(internal val nativeValue: Int) {
    /** ID3v2.3 */
    V2_3(3),

    /** ID3v2.4 (TagLib default) */
    V2_4(4),
}
