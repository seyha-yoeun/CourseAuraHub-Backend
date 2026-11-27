package com.course_aura_hub.chat.service;

import com.course_aura_hub.chat.dto.ChatDtos;
import com.course_aura_hub.common.security.Actor;

/** Searching the messages of my conversations. */
public interface ChatSearchService {

    /**
     * Messages that contain the text (anywhere, also inside Khmer words) in conversations I'm a member of and can
     * still read, newest first, leaving out unsent, hidden and deleted-for-me ones. Filters narrow it down.
     */
    ChatDtos.SearchPage search(Actor actor, ChatDtos.SearchRequest request);
}
