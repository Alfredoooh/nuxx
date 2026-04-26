package com.doction.webviewapp.models

data class FeedVideo(
    val title: String,
    val thumb: String,
    val videoUrl: String,
    val views: String,
    val source: String,
    // TODO: val sourceLabel: String — quando FeedFetcher estiver convertido
)