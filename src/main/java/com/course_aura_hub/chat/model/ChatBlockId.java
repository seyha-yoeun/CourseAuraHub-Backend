package com.course_aura_hub.chat.model;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/** Primary key of {@link ChatBlock}. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ChatBlockId implements Serializable {

    private UUID blockerId;
    private UUID blockedId;
}
