// models/ShortVideo.kt
package com.doction.webviewapp.models

data class ShortVideo(
    val viewKey:       String,
    val title:         String,
    val thumb:         String,
    val videoUrl:      String,
    val likes:         String,
    val views:         String,
    val duration:      String,
    val publisherName: String,
    val publisherThumb:String,
    val publisherUrl:  String,
    val publisherKey:  String,
    val tags:          List<String> = emptyList(),
    var isMuted:       Boolean = false,
    var isLiked:       Boolean = false,
)