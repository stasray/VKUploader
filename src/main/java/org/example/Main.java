package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class Main {

    private static final long GROUP_ID = 227898147;
    private static final String token = "vk1.a._8OLx_yt8gMRE8LLx_JTNuVAssYN9epmfeK41GYatdslg2NDuZMm_8WEJoqHuEdsS40JhiyuSmcJM6UcNABPjo7b5zcNOzaZ_a3lgxHvOu4Z2hBfjOnF0YbQCIXOu_K3HbNm9azxbP4UfyG8MZJdH3BdyYVrhLaLe2sirbuTNZ26cEqKMvZgdQNsMgY6sB0OzxmjhiK_DZnZvJfoSTKO0g";

    private static final PostgresManager db = new PostgresManager("jdbc:postgresql://localhost:5432/postgres", "postgres", "root");;

    private static final int MAX_CONCURRENT_UPLOADS = 5;

    public static void main(String[] args) {

        //db.add(true, "test", 123, 123, "test", "tes", 123, "test");

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);

/*        try {
            askToken("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }*/

        File dir = new File("src/main/resources/vkkiller");
        List<File> lst = new ArrayList<File>();
        for (File file : Objects.requireNonNull(dir.listFiles())) {
            if (file.isFile())
                lst.add(file);
        }

        ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
        UserActor actor = new UserActor(198248840L, token);

        FileSystemManager fsm = new FileSystemManager(vk, actor, GROUP_ID);

        fsm.syncWithVk();
        //fsm.addDirectory("/documents2/photos");
        fsm.delete("documents");
        //fsm.addFile("/documents/photos/", "video.mp4");
        fsm.updateTopic();

        if (true) return;

        lst.forEach(video -> {
            executorService.submit(() -> {
                try {
                    String str = vk.video().save(actor)
                            .groupId(GROUP_ID)
                            .execute().getUploadUrl().toString();
                    out.printf("Получили ссылку для загрузки видео %s. Начинаю загрузку...%n\n", video.getName());
                    uploadVideo(str, video.getAbsolutePath());
                    out.printf("Видео успешно загружено: %s\n", video.getName());
                    saveMetadata(video);
                } catch (ApiException | ClientException | IOException e) {
                    out.println(e.getMessage());
                    throw new RuntimeException(e);
                }
            });
        });

        // Завершение работы пула потоков
        executorService.shutdown();
        try {
            // Ожидание завершения всех задач
            if (!executorService.awaitTermination(60, java.util.concurrent.TimeUnit.SECONDS)) {
                executorService.shutdownNow(); // Принудительное завершение, если задачи не завершились
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
        }
    }


    public static String askToken(String link) throws IOException, URISyntaxException {
        //Opens link in default browser
        Desktop.getDesktop().browse(new URI(link));

        //Asks user to input token from browser manually
        return JOptionPane.showInputDialog("Please input access_token param from browser: ");
    }

    public static void uploadVideo(String uploadUrl, String videoFilePath) throws IOException {
        OkHttpClient client = new OkHttpClient();

        File videoFile = new File(videoFilePath);
        RequestBody fileBody = RequestBody.create(MediaType.parse("application/octet-stream"), videoFile);
        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addPart(
                        Headers.of("Content-Disposition", "form-data; name=\"file\"; filename=\"" + videoFile.getName() + "\""),
                        fileBody
                )
                .build();

        Request request = new Request.Builder()
                .url(uploadUrl)
                .post(requestBody)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("Unexpected code " + response);
            }
        }
    }

    private static void saveMetadata(File video) {
        out.println("Video metadata:");
        out.println("  Name: " + video.getName());
        out.println("  Path: " + video.getAbsolutePath());
        out.println("  Size: " + video.length() + " bytes");
        out.println("  Last modified: " + new java.util.Date(video.lastModified()));
        out.println("  Duration (in seconds): " + getVideoDuration(video));

        try {
            Process process = Runtime.getRuntime().exec("ffprobe -v error -select_streams v:0 -show_entries stream=codec_type,codec_name,width,height,bit_rate,profile -of json " + video.getAbsolutePath());
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String jsonOutput = reader.lines().collect(Collectors.joining("\n"));
            reader.close();

            JSONObject jsonObject = new JSONObject(jsonOutput);
            JSONArray streams = jsonObject.getJSONArray("streams");
            JSONObject videoStream = streams.getJSONObject(0);

            out.println("  Codec type: " + videoStream.getString("codec_type"));
            out.println("  Codec name: " + videoStream.getString("codec_name"));
            out.println("  Resolution: " + videoStream.getInt("width") + "x" + videoStream.getInt("height"));
            out.println("  Bitrate: " + videoStream.getInt("bit_rate") + " kb/s");
            out.println("  Profile: " + videoStream.getString("profile"));

            db.add(true,
                    video.getName(),
                    video.length(),
                    getVideoDuration(video),
                    videoStream.getString("codec_type"),
                    videoStream.getInt("width") + "x" + videoStream.getInt("height"),
                    videoStream.getInt("bit_rate"),
                    videoStream.getString("profile"));

        } catch (IOException | JSONException e) {
            out.println("Error getting additional video metadata: " + e.getMessage());
        }

        out.println("-------------------------------");
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
}
