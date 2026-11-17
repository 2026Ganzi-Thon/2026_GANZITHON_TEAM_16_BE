package com.mowoo.mowoo_backend.attachment;

import com.mowoo.mowoo_backend.attachment.repository.AttachmentRepository;
import com.mowoo.mowoo_backend.attachment.repository.FileDeletionJobRepository;
import com.mowoo.mowoo_backend.attachment.service.FileDeletionService;
import com.mowoo.mowoo_backend.attachment.storage.ObjectStorage;
import com.mowoo.mowoo_backend.character.repository.AiCharacterRepository;
import com.mowoo.mowoo_backend.chat.ai.ChatAiClient;
import com.mowoo.mowoo_backend.chat.ai.ChatAiException;
import com.mowoo.mowoo_backend.chat.entity.ChatSession;
import com.mowoo.mowoo_backend.chat.repository.ChatSessionRepository;
import com.mowoo.mowoo_backend.diary.repository.DiaryRepository;
import com.mowoo.mowoo_backend.record.service.RecordService;
import com.mowoo.mowoo_backend.user.entity.User;
import com.mowoo.mowoo_backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcPrint;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 보안 필터·서비스·MySQL 트랜잭션을 사용하고 객체 저장소와 유료 AI만 대체한다.
@SpringBootTest(properties = {"cloud.aws.s3.enabled=false", "upstage.enabled=false",
        "kakao.client-id=test", "kakao.client-secret=test", "kakao.redirect-uri=http://localhost/test"})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class PhotoAttachmentsIntegrationTest {
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        String url=System.getenv("DB_URL");
        if (url == null || !url.startsWith("jdbc:mysql://")
                || !URI.create(url.substring(5)).getPath().matches("/(?:test|test[_-].*|.*[_-]test|.*[_-]test[_-].*)")) {
            throw new IllegalStateException("사진 테스트는 전용 MySQL 테스트 DB가 필요합니다.");
        }
        registry.add("spring.datasource.url", () -> url);
    }
    @Autowired MockMvc mvc;
    @Autowired JsonMapper json;
    @Autowired UserRepository users;
    @Autowired AiCharacterRepository characters;
    @Autowired com.mowoo.mowoo_backend.character.repository.UserCharacterRepository ownerships;
    @Autowired ChatSessionRepository sessions;
    @Autowired DiaryRepository diaries;
    @Autowired AttachmentRepository attachments;
    @Autowired FileDeletionJobRepository jobs;
    @Autowired FileDeletionService deletion;
    @Autowired com.mowoo.mowoo_backend.attachment.service.FileDeletionWorker worker;
    @Autowired RecordService records;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean ObjectStorage storage;
    @MockitoBean ChatAiClient ai;

    User owner, other;
    Set<String> objects;
    Set<String> createdKeys;
    AtomicBoolean failDeletion;
    byte[] png;

    @BeforeEach
    void setup() throws Exception {
        owner=users.saveAndFlush(new User("photo-owner", null, null));
        other=users.saveAndFlush(new User("photo-other", null, null));
        objects=java.util.concurrent.ConcurrentHashMap.newKeySet();
        createdKeys=java.util.concurrent.ConcurrentHashMap.newKeySet(); failDeletion=new AtomicBoolean();
        var bytes=new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB), "png", bytes);
        png=bytes.toByteArray();
        doAnswer(call -> { String key=call.getArgument(0); objects.add(key); createdKeys.add(key); return null; })
                .when(storage).put(anyString(), any(byte[].class), anyString());
        when(storage.signedUrl(anyString(), anyString())).thenAnswer(call -> "https://private.test/"+call.getArgument(0)+"?signature=test");
        doAnswer(call -> { if (failDeletion.get()) throw new IllegalStateException("test outage"); objects.remove(call.getArgument(0)); return null; })
                .when(storage).delete(anyString());
        when(ai.reply(any())).thenReturn("사진에 관해 어떤 이야기를 하고 싶어?");
    }

    @AfterEach
    void cleanup() {
        failDeletion.set(false);
        for (User user : List.of(owner,other)) {
            if (users.existsById(user.getId())) { records.deleteRecords(user.getId()); users.deleteById(user.getId()); }
        }
        jobs.findAll().stream().filter(j -> createdKeys.contains(j.getObjectKey())).forEach(j -> deletion.attempt(j.getId()));
    }

    @Test
    void createsAndReadsPhotosWithoutLeakingAnotherUsersSignedUrls() throws Exception {
        JsonNode diary=create(owner,2);
        assertThat(diary.path("attachments").size()).isEqualTo(2);
        assertThat(objects).hasSize(2);
        assertThat(createdKeys).allMatch(k -> k.startsWith("uploads/"+owner.getId()+"/") && !k.contains("../"));
        String id=diary.path("id").asString();
        mvc.perform(get("/api/v1/diaries/{id}",id).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachments[0].mimeType").value("image/png"))
                .andExpect(jsonPath("$.attachments[1].sortOrder").value(1));
        mvc.perform(get("/api/v1/diaries").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].attachments.length()").value(2));
        clearInvocations(storage);
        mvc.perform(get("/api/v1/diaries/{id}",id).with(jwt().jwt(j -> j.subject(other.getId().toString()))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    @Test
    void replacesPhotosAndRejectsForeignAttachmentIdsAtomically() throws Exception {
        JsonNode own=create(owner,3), foreign=create(other,1);
        String id=own.path("id").asString();
        String foreignPhoto=foreign.path("attachments").get(0).path("id").asString();
        clearInvocations(storage);
        mvc.perform(patchRequest(id).file(payload("{\"removeAttachmentIds\":[\""+foreignPhoto+"\"]}")).file(photo())
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error.code").value("ATTACHMENT_NOT_FOUND"));
        verifyNoInteractions(storage);
        String removed=own.path("attachments").get(0).path("id").asString();
        mvc.perform(patchRequest(id).file(payload("{\"removeAttachmentIds\":[\""+removed+"\"],\"title\":\"수정\"}")).file(photo())
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.attachments.length()").value(3))
                .andExpect(jsonPath("$.attachments[2].sortOrder").value(3));
        assertThat(attachments.existsById(UUID.fromString(removed))).isFalse();
        assertThat(objects).hasSize(4);
        mvc.perform(patch("/api/v1/diaries/{id}",id).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"removeAttachmentIds\":[\""+foreignPhoto+"\"]}")
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidPayloadAndForgedImagesNeverReachStorage() throws Exception {
        for (MockMultipartFile bad : List.of(new MockMultipartFile("files","fake.png","image/png","<svg/>".getBytes()),
                new MockMultipartFile("files","empty.png","image/png",new byte[0]),
                new MockMultipartFile("files","wrong.jpg","image/jpeg",png))) {
            mvc.perform(multipart("/api/v1/diaries").file(payload(diaryBody())).file(bad)
                            .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_IMAGE"));
        }
        mvc.perform(multipart("/api/v1/diaries").file(payload(diaryBody()))
                        .file(new MockMultipartFile("files","large.png","image/png",new byte[10*1024*1024+1]))
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isContentTooLarge());
        mvc.perform(multipart("/api/v1/diaries").file(payload(diaryBody())).file(photo()).file(photo()).file(photo()).file(photo())
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("TOO_MANY_ATTACHMENTS"));
        for (String bad : List.of("not-json","null","{}",diaryBody().replace("사진 일기",""))) {
            mvc.perform(multipart("/api/v1/diaries").file(payload(bad)).file(photo())
                            .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }
        verifyNoInteractions(storage);
        assertThat(diaries.findSaved(owner.getId(),null,null,org.springframework.data.domain.Limit.of(20))).isEmpty();
    }

    @Test
    void acceptsPhotoOnlyMessageAndPreservesPhotosWhenTextIsEdited() throws Exception {
        ChatSession session=session();
        var result=mvc.perform(multipart("/api/v1/chat-sessions/{id}/messages",session.getId()).file(payload("{}"))
                        .file(photo()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.userMessage.attachments.length()").value(1))
                .andExpect(jsonPath("$.completedTurns").value(1)).andReturn();
        String messageId=json.readTree(result.getResponse().getContentAsString()).path("userMessage").path("id").asString();
        mvc.perform(patch("/api/v1/chat-sessions/{session}/messages/{message}",session.getId(),messageId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"content\":\"오늘 찍은 사진이야\"}")
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userMessage.attachments.length()").value(1));
        verify(ai,times(1)).reply(any());
        mvc.perform(get("/api/v1/chat-sessions/{id}",session.getId()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.messages.items[0].attachments.length()").value(1));
    }

    @Test
    void failedAiRollsBackMessageAndUploadedObject() throws Exception {
        ChatSession session=session();
        when(ai.reply(any())).thenThrow(new ChatAiException("test failure", null));
        mvc.perform(multipart("/api/v1/chat-sessions/{id}/messages",session.getId()).file(payload("{\"content\":\"고민\"}"))
                        .file(photo()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isServiceUnavailable());
        assertThat(createdKeys).hasSize(1);
        assertThat(objects).isEmpty();
        assertThat(attachments.findByUserId(owner.getId())).isEmpty();
        assertThat(jobs.findAll()).noneMatch(j -> createdKeys.contains(j.getObjectKey()));
    }

    @Test
    void partialUploadFailureCleansUpAlreadyUploadedFiles() throws Exception {
        var count=new java.util.concurrent.atomic.AtomicInteger();
        doAnswer(call -> {
            String key=call.getArgument(0); createdKeys.add(key); objects.add(key);
            if (count.incrementAndGet()==2) throw new IllegalStateException("ambiguous upload timeout");
            return null;
        }).when(storage).put(anyString(),any(byte[].class),anyString());
        mvc.perform(multipart("/api/v1/diaries").file(payload(diaryBody())).file(photo()).file(photo())
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.error.code").value("STORAGE_UNAVAILABLE"));
        assertThat(createdKeys).hasSize(2);
        assertThat(objects).isEmpty();
        assertThat(attachments.findByUserId(owner.getId())).isEmpty();
    }

    @Test
    void deletionFailureKeepsDurableJobAndRetryRemovesObject() throws Exception {
        var diary=create(owner,1);
        failDeletion.set(true);
        mvc.perform(post("/api/v1/diaries/bulk-delete").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"diaryIds\":[\""+diary.path("id").asString()+"\"]}")
                        .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk());
        assertThat(attachments.findByUserId(owner.getId())).isEmpty();
        assertThat(objects).hasSize(1);
        var job=jobs.findAll().stream().filter(j -> createdKeys.contains(j.getObjectKey())).findFirst().orElseThrow();
        failDeletion.set(false);
        deletion.attempt(job.getId());
        assertThat(objects).isEmpty();
        assertThat(jobs.existsById(job.getId())).isFalse();
    }

    @Test
    void databaseRollbackDoesNotDeleteExistingObjects() throws Exception {
        var diary=create(owner,1);
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            records.deleteRecords(owner.getId());
            tx.setRollbackOnly();
        });
        assertThat(diaries.existsById(UUID.fromString(diary.path("id").asString()))).isTrue();
        assertThat(attachments.findByUserId(owner.getId())).hasSize(1);
        assertThat(objects).hasSize(1);
        verify(storage,never()).delete(anyString());
    }

    @Test
    void deletingConversationPreservesDiaryPhotosAndWithdrawalCleansTheRest() throws Exception {
        var diary=create(owner,1);
        ChatSession session=session();
        mvc.perform(multipart("/api/v1/chat-sessions/{id}/messages",session.getId()).file(payload("{\"content\":\"사진\"}"))
                        .file(photo()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isCreated());
        mvc.perform(delete("/api/v1/chat-sessions/{id}",session.getId()).with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk());
        assertThat(objects).hasSize(1);
        assertThat(diaries.existsById(UUID.fromString(diary.path("id").asString()))).isTrue();
        mvc.perform(delete("/api/v1/auth/withdraw").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isOk());
        assertThat(objects).isEmpty();
        assertThat(users.existsById(owner.getId())).isFalse();
    }

    @Test
    void unauthenticatedAndForeignUploadsHaveNoSideEffects() throws Exception {
        String id=create(owner,1).path("id").asString();
        clearInvocations(storage);
        mvc.perform(multipart("/api/v1/diaries").file(payload(diaryBody())).file(photo()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patchRequest(id).file(payload("{}")).file(photo()).with(jwt().jwt(j -> j.subject(other.getId().toString()))))
                .andExpect(status().isNotFound());
        verifyNoInteractions(storage);
    }

    private JsonNode create(User user,int count) throws Exception {
        var request=multipart("/api/v1/diaries").file(payload(diaryBody()));
        for(int i=0;i<count;i++) request.file(photo());
        var result=mvc.perform(request.with(jwt().jwt(j -> j.subject(user.getId().toString()))))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void recoversUploadIntentsAfterProcessDeathWithoutDeletingCommittedPhotos() throws Exception {
        create(owner,1);
        String committedKey=createdKeys.iterator().next();
        var committedJob=jobs.saveAndFlush(new com.mowoo.mowoo_backend.attachment.entity.FileDeletionJob(committedKey));
        clearInvocations(storage);
        worker.delete(committedJob.getId());
        assertThat(objects).contains(committedKey);
        verify(storage,never()).delete(committedKey);
        assertThat(jobs.existsById(committedJob.getId())).isFalse();

        String abandonedKey="uploads/abandoned/"+UUID.randomUUID()+".png";
        storage.put(abandonedKey,png,"image/png");
        var abandoned=worker.beginUpload(abandonedKey);
        assertThat(jobs.findById(abandoned).orElseThrow().getNextAttemptAt()).isAfter(java.time.Instant.now().plusSeconds(3600));
        worker.delete(abandoned);
        assertThat(objects).doesNotContain(abandonedKey);
        assertThat(jobs.existsById(abandoned)).isFalse();
    }

    @Test
    void concurrentPhotoAddsCannotExceedThePerDiaryLimit() throws Exception {
        String id=create(owner,2).path("id").asString();
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Integer> add=() -> mvc.perform(patchRequest(id).file(payload("{}")).file(photo())
                            .with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                    .andReturn().getResponse().getStatus();
            var results=executor.invokeAll(List.of(add,add));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(200,400);
        }
        assertThat(attachments.findByUserId(owner.getId())).hasSize(3);
        assertThat(objects).hasSize(3);
    }

    @Test
    void swaggerDocumentsJsonAndMultipartOnTheSameThreeOperations() throws Exception {
        var response=mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn();
        var paths=json.readTree(response.getResponse().getContentAsString()).path("paths");
        for(String[] route:List.of(new String[]{"/api/v1/diaries","post"},
                new String[]{"/api/v1/diaries/{diaryId}","patch"},
                new String[]{"/api/v1/chat-sessions/{sessionId}/messages","post"})) {
            var content=paths.path(route[0]).path(route[1]).path("requestBody").path("content");
            assertThat(content.has("application/json")).as(route[0]+" JSON").isTrue();
            assertThat(content.has("multipart/form-data")).as(route[0]+" multipart").isTrue();
        }
    }
    private ChatSession session() throws Exception {
        var chosen=characters.findByCodeInAndPremiumFalse(List.of("KIND")).getFirst();
        ownerships.saveAndFlush(new com.mowoo.mowoo_backend.character.entity.UserCharacter(owner,chosen));
        String character=chosen.getId().toString();
        var response=mvc.perform(post("/api/v1/chat-sessions").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"characterId\":\""+character+"\"}").with(jwt().jwt(j -> j.subject(owner.getId().toString()))))
                .andExpect(status().isCreated()).andReturn();
        return sessions.findById(UUID.fromString(json.readTree(response.getResponse().getContentAsString()).path("id").asString())).orElseThrow();
    }
    private MockMultipartHttpServletRequestBuilder patchRequest(String id) {
        return multipart(org.springframework.http.HttpMethod.PATCH,"/api/v1/diaries/{id}",id);
    }
    private MockMultipartFile payload(String text) { return new MockMultipartFile("payload","","text/plain",text.getBytes(java.nio.charset.StandardCharsets.UTF_8)); }
    private MockMultipartFile photo() { return new MockMultipartFile("files","../../image.png","image/png",png); }
    private String diaryBody() { return "{\"entryDate\":\"2026-11-17\",\"title\":\"사진 일기\",\"content\":\"사진을 남겼다\"}"; }
}
