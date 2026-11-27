package com.course_aura_hub.chat.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A private file sent with a message; read links are signed per request. */
@Entity
@Table(name = "chat_attachments")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Integer id;

    @Column(name = "message_id", nullable = false, updatable = false)
    private Integer messageId;

    /** storage:// reference of the uploaded file. */
    @Column(name = "file_ref", nullable = false, updatable = false, columnDefinition = "text")
    private String fileRef;

    @Column(name = "file_name", nullable = false, updatable = false, columnDefinition = "text")
    private String fileName;

    @Column(name = "content_type", nullable = false, updatable = false, columnDefinition = "text")
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;
}
