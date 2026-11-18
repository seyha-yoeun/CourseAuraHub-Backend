package com.course_aura_hub.video.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTranscriptTest {

    @Test
    void keepsOnlyTheSpokenText() {
        String vtt = """
                ﻿WEBVTT

                NOTE written by the instructor
                for this lesson

                intro
                00:00:01.000 --> 00:00:04.000
                Welcome to <b>Git</b> basics.

                2
                00:04.000 --> 00:08.000 align:start
                Welcome to <b>Git</b> basics.
                Today we commit.
                """;

        assertThat(VideoServiceImpl.toText(vtt)).isEqualTo("Welcome to Git basics. Today we commit.");
    }
}
