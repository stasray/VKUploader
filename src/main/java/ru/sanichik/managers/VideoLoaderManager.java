package ru.sanichik.managers;

import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.objects.video.VideoFull;
import com.vk.api.sdk.objects.video.responses.GetResponse;
import com.vk.api.sdk.objects.video.responses.SaveResponse;
import okhttp3.*;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;
import ru.sanichik.core.Main;
import ru.sanichik.utils.Utils;
import ru.sanichik.exceptions.DirectoryNotFoundException;
import ru.sanichik.exceptions.FileAlreadyExistsException;
import ru.sanichik.objects.VideoObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class VideoLoaderManager {
    private final VkApiClient vk;
    private final UserActor actor;
    private final Long groupId;
    ExecutorService executorService = Executors.newFixedThreadPool(Main.MAX_CONCURRENT_UPLOADS);

    private static final ConcurrentHashMap<String, Integer> progressMap = new ConcurrentHashMap<>();


    public VideoLoaderManager(final VkApiClient vk, final UserActor actor, final Long groupId) {
        this.vk = vk;
        this.actor = actor;
        this.groupId = groupId;
    }

    public int getProgress(String directory, String video) {
        return progressMap.getOrDefault(directory + video, 0);
    }

    public Map<String, Integer> getProgresses(String directory) {
        directory = Utils.normalizePath(directory);
        HashMap<String, Integer> map = new HashMap<>();
        for (String path : progressMap.keySet()) {
            String title = path.substring(directory.length());
            if (path.startsWith(directory) &&
            !title.contains("/")) {
               map.put(title, progressMap.get(path));
            }
        }
        return map;
    }

    public void loadVideo(String currDirectory, List<File> files, FileSystemManager fsm) {
        AtomicInteger id = new AtomicInteger();
        final int size = files.size();
        long startTime = System.currentTimeMillis();

        files.forEach(video -> {
            progressMap.put(currDirectory + video.getName(), 0);
            executorService.submit(() -> {
                try {
                    id.getAndIncrement();
                    String fileName = video.getName();
                    String fileNameWithoutExtension = fileName.substring(0, fileName.lastIndexOf('.'));

                    SaveResponse videoUploadResponse;
                    String meta = getMetadata(video);

                    if (fsm instanceof FileSystemAlbumsManager &&
                            ((FileSystemAlbumsManager) fsm).getAlbumID(currDirectory) != null
                    ) {
                        int albumId = ((FileSystemAlbumsManager) fsm).getAlbumID(currDirectory);
                        videoUploadResponse = vk.video().save(actor)
                                .groupId(groupId)
                                .albumId(albumId)
                                .name(fileNameWithoutExtension)
                                .description(meta)
                                .execute();
                    } else {
                        videoUploadResponse = vk.video().save(actor)
                                .groupId(groupId)
                                .name(fileNameWithoutExtension)
                                .description(meta)
                                .execute();
                    }

                    out.printf("Sending video %s...%n", video.getName());
                    uploadVideo(videoUploadResponse
                            .getUploadUrl().toString(),
                            video.getAbsolutePath(),
                            currDirectory + video.getName()
                    );

                    progressMap.put(currDirectory + video.getName(), 100);

                    Main.getFileSystemManager().addFile(currDirectory, new VideoObject(videoUploadResponse.getVideoId(),
                            fileNameWithoutExtension, meta));
                    out.printf("Video successful loaded: %s\n", video.getName());

                    if (id.get() == size) {
                        out.println("All videos loaded with " + (System.currentTimeMillis() - startTime) + "ms");
                        Main.getFileSystemManager().updateTopic();
                        Main.getFileSystemManager().syncWithVk();
                    }

                } catch (ApiException | ClientException | IOException e) {
                    progressMap.put(currDirectory + video.getName(), -1);
                    out.println(e.getMessage());
                    out.printf("Trying again for %s...\n", video.getName());
                    loadVideo(currDirectory, List.of(video), fsm);
                    throw new RuntimeException(e);
                } catch (FileAlreadyExistsException |
                         DirectoryNotFoundException e) {
                    out.println(e.getMessage());
                    progressMap.put(currDirectory + video.getName(), -1);
                    throw new RuntimeException(e);
                }
            });
        });
       /*executorService.shutdown();

        try {
            // Ожидание завершения всех задач
            if (!executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Принудительное завершение, если задачи не завершились
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }*/
    }


    public void deleteVideosFromFolder(String path) throws ClientException, ApiException {
        List<VideoFull> list = vk.video().get(actor).ownerId(groupId * -1L).execute().getItems();
        for (VideoFull video : list) {
            if (video.getDescription().split("\\R")[0].startsWith(Utils.normalizePath(path))) {
                QueueManager.requestQueue.offer(() -> {
                    try {
                        vk.video().delete(actor, video.getId()).ownerId(groupId * -1L).execute();
                        out.println("Removing videofile from album...");
                    } catch (ApiException | ClientException e) {
                        out.println("Error deleting video: " + e.getMessage());
                    }
                });
            }
        }
    }

    public void deleteVideo(VideoObject video) throws ClientException, ApiException {
        out.println("Removing videofile from album...");
        vk.video().delete(actor, video.getId()).ownerId(groupId * -1L).execute();
        out.println("Video removed!");

    }

    public URI getVideoLink(VideoObject video) throws ClientException, ApiException {
        GetResponse response = vk.video().get(actor)
                .ownerId(-groupId)
                .videos("-" + groupId + "_" + video.getId())
                .extended(true)
                .execute();

        List<VideoFull> videos = response.getItems();
        if (videos == null || videos.isEmpty()) {
            throw new ClientException("Link not found for " + video.getTitle());
        }

        return videos.get(0).getPlayer();
    }

    private static String getMetadata(File video) {
        StringBuilder builder = new StringBuilder();

        builder.append("Video metadata:\n");
        builder.append("  Name: ").append(video.getName()).append("\n");
        builder.append("  Size: ").append(video.length()).append(" bytes\n");
        builder.append("  Last modified: ").append(new java.util.Date(video.lastModified())).append("\n");
        builder.append("  Duration (in seconds): ").append(getVideoDuration(video)).append("\n");


        try {
            // Запуск ffprobe для получения данных в формате JSON
            Process process = Runtime.getRuntime().exec("ffprobe -v error -show_entries stream=codec_type,codec_name,width,height,bit_rate,profile -of json " + video.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String jsonOutput = reader.lines().collect(Collectors.joining("\n"));
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonOutput);
            JSONArray streams = jsonObject.getJSONArray("streams");

            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                String codecType = stream.getString("codec_type");

                // Обработка видеодорожек
                if ("video".equals(codecType)) {
                    builder.append("Video Track:\n");
                    builder.append("  Codec name: ").append(stream.getString("codec_name")).append("\n");
                    builder.append("  Resolution: ")
                            .append(stream.getInt("width")).append("x").append(stream.getInt("height")).append("\n");
                    builder.append("  Bitrate: ").append(stream.optInt("bit_rate", 0)).append(" kb/s\n");
                    builder.append("  Profile: ").append(stream.optString("profile", "N/A")).append("\n");
                    builder.append("\n");
                }

                // Обработка аудиодорожек
                if ("audio".equals(codecType)) {
                    builder.append("Audio Track:\n");
                    builder.append("  Codec name: ").append(stream.getString("codec_name")).append("\n");
                    builder.append("  Bitrate: ").append(stream.optInt("bit_rate", 0)).append(" kb/s\n");
                    builder.append("  Channels: ").append(stream.optInt("channels", 0)).append("\n");
                    builder.append("  Sample rate: ").append(stream.optInt("sample_rate", 0)).append(" Hz\n");
                    builder.append("\n");
                }
            }

            return builder.toString();
        } catch (IOException | JSONException e) {
            out.println("Error getting additional video metadata: " + e.getMessage());
        }
        return "Error while getting metadata.";
    }


    private static float getVideoDuration(File video) {
        try {
            Process process = Runtime.getRuntime().exec("ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 " + video.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String durationStr = reader.readLine();
            reader.close();
            return Float.parseFloat(durationStr);
        } catch (IOException e) {
            out.println("Error getting video duration: " + e.getMessage());
            return -1;
        }
    }
    public static void uploadVideo(String uploadUrl, String videoFilePath, String fullname) throws IOException {
        OkHttpClient client = new OkHttpClient();

        File videoFile = new File(videoFilePath);
        RequestBody fileBody = new ProgressRequestBody(videoFile, "application/octet-stream", fullname);

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", videoFile.getName(), fileBody)
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.out.println("Upload failed for " + videoFile.getName() + " " + response);
                throw new IOException("Unexpected code " + response);
            }
            System.out.println("Upload complete for " + videoFile.getName());
        }
    }

    static class ProgressRequestBody extends RequestBody {
        private final File file;
        private final String contentType;
        private final String fullname;

        public ProgressRequestBody(File file, String contentType, String fullname) {
            this.file = file;
            this.contentType = contentType;
            this.fullname = fullname;
        }

        @Override
        public MediaType contentType() {
            return MediaType.parse(contentType);
        }

        @Override
        public long contentLength() {
            return file.length();
        }

        @Override
        public void writeTo(BufferedSink sink) throws IOException {
            long fileLength = file.length();
            long totalBytesRead = 0;
            int bufferSize = 8192;
            byte[] buffer = new byte[bufferSize];

            long lastProgressUpdate = System.nanoTime();

            try (BufferedSource source = Okio.buffer(Okio.source(file))) {
                long bytesRead;
                while ((bytesRead = source.read(buffer)) != -1) {
                    sink.write(buffer, 0, (int) bytesRead);
                    totalBytesRead += bytesRead;

                    long now = System.nanoTime();
                    if ((now - lastProgressUpdate) > 500_000_000) { // Обновлять раз в 500 мс
                        int progress = (int) ((totalBytesRead * 100) / fileLength);
                        progressMap.put(fullname, progress);
                        lastProgressUpdate = now;
                    }
                }
            }
        }
    }
}
