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
import java.util.stream.Collectors;

import static java.lang.System.out;

public class Main {

    private static final long GROUP_ID = 227898147;
    private static final String token = "vk1.a.LnuaYLZ64A0pbow_txcAatzNEhbYrVn_o7EZDTIdARwUPYgfo1M-Qq9lavaI4SiXiUUXwfFmDDd11ZwlMt2wTVCuU-QXbvc3SuMTEzT-ajR0yQQ_TV0bf_OFWypYiI6KeZH-g8hurnlOy3C3PTOq9MlCDLcEWpAeCSfwmXvyE_LnyC4lHLKb9IXkZ6cAYQT0";

    private static final PostgresManager db = new PostgresManager("jdbc:postgresql://localhost:5432/videos", "postgres", "0402");;

    public static void main(String[] args) {
        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);

        // Код, чтобы получить токен
/*        try {
            askToken("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video&response_type=token&v=5.59");
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

        UserActor actor = new UserActor(198248840L, token);
        lst.forEach(video -> {
            Thread t = new Thread(new Runnable() {
                @Override
                public void run() {
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
                }
            });
            t.start();

        });
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