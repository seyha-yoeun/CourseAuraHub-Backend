package com.course_aura_hub.file;

import com.course_aura_hub.file.repository.StoredFileRepository;
import com.course_aura_hub.file.service.FileService;
import com.course_aura_hub.file.storage.ObjectStore;
import com.course_aura_hub.learner.LearnerIntegrationTestSupport;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FileIntegrationTest extends LearnerIntegrationTestSupport {

    private static final String FILES = "/api/v1/me/files";
    private static final byte[] PNG_START = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
    private static final long SIZE = 1024;

    @MockitoBean
    private ObjectStore objectStore;

    @Autowired
    private FileService fileService;

    @Autowired
    private StoredFileRepository fileRepository;

    @BeforeEach
    void stubStorage() {
        reset(objectStore);
        when(objectStore.presignUpload(anyString(), anyString(), anyLong(), any())).thenReturn("http://storage/upload");
        when(objectStore.size(anyString())).thenReturn(Optional.of(SIZE));
        when(objectStore.readStart(anyString(), anyInt())).thenReturn(PNG_START);
    }

    private ResultActions start(TestUser user, String purpose, String contentType, long size) throws Exception {
        return mockMvc.perform(asJson(user, post(FILES), Map.of(
                "purpose", purpose, "fileName", "photo.png", "contentType", contentType, "size", size)));
    }

    private String startPng(TestUser user, String purpose) throws Exception {
        String body = start(user, purpose, "image/png", SIZE)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.id");
    }

    private ResultActions complete(TestUser user, String id) throws Exception {
        return mockMvc.perform(as(user, post(FILES + "/" + id + "/complete")));
    }

    private String uploadAvatar(TestUser user) throws Exception {
        String body = complete(user, startPng(user, "AVATAR"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.url");
    }

    @Test
    void avatar_landsInPublicStorageAfterTheCheck() throws Exception {
        TestUser learner = registerUser();
        String id = startPng(learner, "AVATAR");

        complete(learner, id)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.containsString("/public/avatars/" + id + ".png")));

        verify(objectStore).move("uploads/" + id, "public/avatars/" + id + ".png");
    }

    @Test
    void uploads_requireSigningIn() throws Exception {
        mockMvc.perform(post(FILES).with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void courseAndLessonFiles_areAdminOnly() throws Exception {
        TestUser learner = registerUser();

        start(learner, "COURSE_THUMBNAIL", "image/png", SIZE).andExpect(status().isForbidden());
        start(learner, "LESSON_CONTENT", "video/mp4", SIZE).andExpect(status().isForbidden());
        start(registerAdmin(), "LESSON_CONTENT", "video/mp4", SIZE).andExpect(status().isCreated());
    }

    @Test
    void unsupportedTypesAndOversizedFiles_areRejected() throws Exception {
        TestUser learner = registerUser();

        start(learner, "AVATAR", "image/svg+xml", SIZE).andExpect(status().isBadRequest());
        start(learner, "AVATAR", "video/mp4", SIZE).andExpect(status().isBadRequest());
        start(learner, "AVATAR", "image/png", 6L * 1024 * 1024).andExpect(status().isBadRequest());
        start(learner, "NOT_A_PURPOSE", "image/png", SIZE).andExpect(status().isBadRequest());
        verify(objectStore, never()).presignUpload(anyString(), anyString(), anyLong(), any());
    }

    @Test
    void fileWhoseContentLies_isDeleted() throws Exception {
        TestUser learner = registerUser();
        String id = startPng(learner, "AVATAR");
        when(objectStore.readStart(anyString(), anyInt())).thenReturn("<svg onload=x>".getBytes());

        complete(learner, id).andExpect(status().isBadRequest());

        verify(objectStore).delete("uploads/" + id);
        verify(objectStore, never()).move(anyString(), anyString());
        assertThat(fileRepository.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    void wrongSize_isDeleted() throws Exception {
        TestUser learner = registerUser();
        String id = startPng(learner, "AVATAR");
        when(objectStore.size(anyString())).thenReturn(Optional.of(SIZE * 100));

        complete(learner, id).andExpect(status().isBadRequest());

        verify(objectStore, never()).move(anyString(), anyString());
    }

    @Test
    void unfinishedUpload_canBeRetried() throws Exception {
        TestUser learner = registerUser();
        String id = startPng(learner, "AVATAR");
        when(objectStore.size(anyString())).thenReturn(Optional.empty());

        complete(learner, id).andExpect(status().isBadRequest());

        when(objectStore.size(anyString())).thenReturn(Optional.of(SIZE));
        complete(learner, id).andExpect(status().isOk());
    }

    @Test
    void completing_isOnlyForTheUploader() throws Exception {
        String id = startPng(registerUser(), "AVATAR");

        complete(registerUser(), id).andExpect(status().isNotFound());
    }

    @Test
    void uploads_areLimitedPerHour() throws Exception {
        TestUser learner = registerUser();
        for (int i = 0; i < 20; i++) {
            startPng(learner, "AVATAR");
        }

        start(learner, "AVATAR", "image/png", SIZE).andExpect(status().isBadRequest());
    }

    @Test
    void replacedAvatar_isReleasedAndCleanedUp() throws Exception {
        TestUser learner = registerUser();
        String first = uploadAvatar(learner);
        mockMvc.perform(asJson(learner, patch("/api/v1/me/profile"), Map.of("imageUrl", first)))
                .andExpect(status().isOk());

        mockMvc.perform(asJson(learner, patch("/api/v1/me/profile"), Map.of("imageUrl", uploadAvatar(learner))))
                .andExpect(status().isOk());
        jdbcTemplate.update("update stored_files set updated_at = now() - interval '2 days' where status = 'RELEASED'");
        fileService.cleanUp();

        verify(objectStore).delete(startsWith("public/avatars/"));
    }

    @Test
    void someoneElsesAvatar_isNeverReleased() throws Exception {
        String victimUrl = uploadAvatar(registerUser());
        TestUser other = registerUser();
        mockMvc.perform(asJson(other, patch("/api/v1/me/profile"), Map.of("imageUrl", victimUrl)))
                .andExpect(status().isOk());

        mockMvc.perform(asJson(other, patch("/api/v1/me/profile"), Map.of("imageUrl", "")))
                .andExpect(status().isOk());

        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from stored_files where status = 'RELEASED' and object_key = ?",
                Long.class, victimUrl.substring(victimUrl.indexOf("public/")))).isZero();
    }

    @Test
    void abandonedUpload_isCleanedUp() throws Exception {
        String id = startPng(registerUser(), "AVATAR");
        jdbcTemplate.update("update stored_files set updated_at = now() - interval '2 days' where id = ?", UUID.fromString(id));

        fileService.cleanUp();

        verify(objectStore).delete("uploads/" + id);
        assertThat(fileRepository.findById(UUID.fromString(id))).isEmpty();
    }

    @Test
    void privateLessonFile_isSignedOnlyForPeopleWithAccess() throws Exception {
        when(objectStore.presignDownload(eq("private/lessons/a.mp4"), any())).thenReturn("https://signed/a.mp4?sig=1");
        String curriculum = "/api/v1/courses/" + FREE_COURSE + "/curriculum";
        String body = mockMvc.perform(get(curriculum)).andReturn().getResponse().getContentAsString();
        Integer lockedLesson = JsonPath.read(body, "$.data.sections[0].lessons[1].id");
        jdbcTemplate.update("update lessons set content_url = 'storage://private/lessons/a.mp4' where id = ?", lockedLesson);

        mockMvc.perform(get(curriculum))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").doesNotExist());
        verify(objectStore, never()).presignDownload(anyString(), any());

        TestUser learner = registerUser();
        enroll(learner, FREE_COURSE);
        mockMvc.perform(as(learner, get(curriculum)))
                .andExpect(jsonPath("$.data.sections[0].lessons[1].contentUrl").value("https://signed/a.mp4?sig=1"));
    }

    @Test
    void signedLinkSentBackByTheForm_becomesAReferenceAgain() {
        assertThat(fileService.toStoredUrl("http://localhost:9000/course-aura/private/lessons/a.mp4?X-Amz-Signature=1"))
                .isEqualTo("storage://private/lessons/a.mp4");
        assertThat(fileService.toStoredUrl("https://youtu.be/abc")).isEqualTo("https://youtu.be/abc");
    }
}
