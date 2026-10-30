package com.kyant.taglib

internal data class MpegTagInfo(
    val hasId3v1: Boolean,
    val hasId3v2: Boolean,
    val hasApe: Boolean,
    val id3v2MajorVersion: Int,
    val id3v1Title: String?,
    val id3v2Title: String?,
    val apeTitle: String?
)
