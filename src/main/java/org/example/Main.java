package org.example;

import com.vk.api.sdk.client.TransportClient;
import com.vk.api.sdk.client.VkApiClient;
import com.vk.api.sdk.client.actors.UserActor;
import com.vk.api.sdk.exceptions.ApiException;
import com.vk.api.sdk.exceptions.ClientException;
import com.vk.api.sdk.httpclient.HttpTransportClient;
import com.vk.api.sdk.objects.video.VideoFull;
import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static java.lang.System.out;

public class Main {

    private static long groupId = 227898147;
    private static boolean gui = true;
    private static String token = "vk1.a.dm-tZMbMwdi1FB0ZLMZ97TFSOVBmPjR8cYt7xc5lWkPBUp377FZ2-qTGAVaXnY1Ky-VuKQdNB5jMGkwL1nVCQI_Z0HUklDpRCmdy7xhvT7Q_4Uoduh6xCvjf3EMqMV0tmqpKy1SIxyPcoRaqaG7PPIpkvWy-jkv70e794c2srp3x26Z9n7ZWj-5awrUHHXdwDMHj3gF9J0y1fdGgiFnR_g";

    private static final PostgresManager db = new PostgresManager("jdbc:postgresql://localhost:5432/postgres", "postgres", "root");;

    private static int MAX_CONCURRENT_UPLOADS = 5;

    private static String currDirectory = "/";

    public static void main(String[] serverargs) {

        Scanner scanner = new Scanner(System.in);
        for (String arg : serverargs) {
            if (arg.startsWith("-token=")) {
                token = arg.replaceFirst("-token=", "");
                continue;
            }
            if (arg.startsWith("-groupid=")) {
                groupId = Long.parseLong(arg.replaceFirst("-groupid=", ""));
                continue;
            }
            if (arg.startsWith("-max_concurrent=")) {
                MAX_CONCURRENT_UPLOADS = Integer.parseInt(arg.replaceFirst("-max_concurrent=", ""));
                if (MAX_CONCURRENT_UPLOADS < 1) {
                    System.out.println("Error! max_concurrent cannot be lower than 1. Using default value: 5");
                    MAX_CONCURRENT_UPLOADS = 5;
                }
                continue;
            }
            if (arg.equalsIgnoreCase("-nogui")) {
                gui = false;
                continue;
            }
        }
        out.println("VKVideoUploader started. Use \"help\" for list of commands.");

        /**TODO: check token*/

        TransportClient transportClient = new HttpTransportClient();
        VkApiClient vk = new VkApiClient(transportClient);
        UserActor actor = new UserActor(198248840L, token);
        FileSystemManager fsm = new FileSystemManager(vk, actor, groupId);
        ExecutorService executorService = Executors.newFixedThreadPool(MAX_CONCURRENT_UPLOADS);
        fsm.syncWithVk();

        while (true) {
            out.print(currDirectory + " > ");
            String[] cmdfull = scanner.nextLine().trim().split(" ");
            String cmd = cmdfull[0];
            String[] args = Arrays.copyOfRange(cmdfull, 1, cmdfull.length);

            if (cmd.equalsIgnoreCase("exit")) {
                out.println("VKVideoUploader stopped.");
                break;
            }
            if (cmd.equalsIgnoreCase("help")) {
                out.println("Commands:");
                out.println(" exit - stop server");
                out.println(" ls - check files and subfolders in current folder.");
                out.println(" mkdir <name> - create folder");
                out.println(" cd <path> - go to path folder");
                out.println(" rmdir <name> - remove folder with all files and subfolders. WARNING! It will remove all video files in folder and subfolders!");
                out.println(" rmfile <name> - remove file in current directory.");
                out.println(" addvideo <path> - export videos from local path to VK. It can be folder with files or single file. Use -meta to save meta to comment.");
                continue;
            }
            if (cmd.equalsIgnoreCase("ls")) {
                fsm.syncWithVk();
                //TODO: учесть, что папка может быть удалена в момент использования
                out.println("Files and folders in " + currDirectory);
                fsm.getFolders(currDirectory).forEach(s -> {
                    s = s.substring(currDirectory.length());

                    int count=0;
                    for (char element : s.toCharArray()) {
                        if (element == '/') count++;
                    }
                    if (count <= 1) {
                        s = s.substring(0, s.indexOf("/"));
                        out.println(s);
                    }
                });
                fsm.getFiles(currDirectory).forEach(out::println);
                continue;
            }
            if (cmd.equalsIgnoreCase("cd")) {
                if (args.length != 1) {
                    out.println("Usage: cd <path>");
                    continue;
                }

                String targetPath = args[0];
                if (targetPath.equals("..")) {
                    // Переход на уровень выше
                    if (!currDirectory.equals("/")) {
                        currDirectory = currDirectory.substring(0, currDirectory.lastIndexOf("/"));
                        currDirectory = currDirectory.substring(0, currDirectory.lastIndexOf("/")) + '/';
                        if (currDirectory.isEmpty()) {
                            currDirectory = "/";
                        }
                    }
                } else {
                    // Переход в указанную директорию
                    String newPath;
                    if (targetPath.startsWith("/")) {
                        // Абсолютный путь
                        newPath = targetPath;
                    } else {
                        // Относительный путь
                        newPath = currDirectory.endsWith("/") ? currDirectory + targetPath : currDirectory + "/" + targetPath;
                    }

                    newPath = FileSystemManager.normalizePath(newPath);

                    if (fsm.isDirectoryExists(newPath)) {
                        currDirectory = newPath;
                    } else {
                        out.println("Directory not found: " + targetPath);
                    }
                }

                continue;
            }

            if (cmd.equalsIgnoreCase("mkdir")) {
                if (args.length != 1) {
                    out.println("Usage: mkdir <folder_name>");
                    continue;
                }
                String foldername = args[0];
                if (!foldername.matches("[0-9a-zA-Zа-яА-Я_-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.addDirectory(currDirectory + foldername);
                    fsm.updateTopic();
                    out.println("Folder created.");
                } catch (FileSystemManager.DirectoryAlreadyExistsException e) {
                    out.println("Folder already exists.");
                }
                continue;
            }
            if (cmd.equalsIgnoreCase("rmdir")) {
                if (args.length != 1) {
                    out.println("Usage: rmdir <folder_name>");
                    continue;
                }
                String foldername = args[0];
                if (!foldername.matches("[0-9a-zA-Zа-яА-Я_-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.deleteFolder(currDirectory + foldername);
                    fsm.updateTopic();

                    out.println("Loading");
                    List<VideoFull> list = vk.video().get(actor).ownerId(groupId * -1L).execute().getItems();
                    for (VideoFull video : list) {
                        if (video.getTitle().startsWith( FileSystemManager.normalizePath(currDirectory + foldername) )) {
                            vk.video().delete(actor, video.getId()).ownerId(groupId * -1L).execute();
                            out.println("Removing videofile from album...");
                        }
                    }
                    out.println("Files removed.");
                    continue;
                } catch (FileSystemManager.DirectoryNotFoundException e) {
                    out.println("Folder not found.");
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                } catch (ApiException e) {
                    throw new RuntimeException(e);
                }
            }
            if (cmd.equalsIgnoreCase("rmfile")) {
                if (args.length != 1) {
                    out.println("Usage: rmfile <name>");
                    continue;
                }
                String filename = args[0];
                if (!filename.matches("[0-9a-zA-Zа-яА-Я._-]+")) {
                    out.println("Error. Allowed characters: 0-9, a-z, A-Z, а-я, А-Я, '_', '-', '.'");
                    continue;
                }
                fsm.syncWithVk();
                try {
                    fsm.deleteFile(currDirectory, filename);
                    fsm.updateTopic();
                    out.println("Loading");
                    List<VideoFull> list = vk.video().get(actor).ownerId(groupId * -1L).execute().getItems();
                    for (VideoFull video : list) {
                        if (video.getTitle().equals(currDirectory + filename)) {
                            vk.video().delete(actor, video.getId()).ownerId(groupId * -1L).execute();
                            out.println("Removing videofile from album...");
                        }
                    }
                    out.println("File removed.");
                    continue;
                } catch (FileSystemManager.FileNotFoundException e) {
                    out.println("File not found.");
                    continue;
                } catch (FileSystemManager.DirectoryNotFoundException e) {
                    out.println("Directory not found.");
                    continue;
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                } catch (ApiException e) {
                    throw new RuntimeException(e);
                }
            }
            if (cmd.equalsIgnoreCase("addvideo")) {
                if (args.length == 0) {
                    out.println("Usage: addvideo <path>");
                    continue;
                }

                File dir = new File(args[0]);
                List<File> lst = new ArrayList<File>();
                if (dir.isDirectory()) {
                    for (File file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.isFile())
                            lst.add(file);
                    }
                } else {
                    lst.add(dir);
                }


                lst.forEach(video -> {
                    executorService.submit(() -> {
                        try {
                            String str = vk.video().save(actor)
                                    .groupId(groupId)
                                    .name(currDirectory + video.getName())
                                    .execute().getUploadUrl().toString();
                            out.printf("Получили ссылку для загрузки видео %s. Начинаю загрузку...%n\n", video.getName());
                            uploadVideo(str, video.getAbsolutePath());
                            fsm.addFile(currDirectory, video.getName());
                            fsm.updateTopic();
                            out.printf("Видео успешно загружено: %s\n", video.getName());
                            //saveMetadata(video);
                        } catch (ApiException | ClientException | IOException e) {
                            out.println(e.getMessage());
                            throw new RuntimeException(e);
                        } catch (FileSystemManager.FileAlreadyExistsException |
                                 FileSystemManager.DirectoryNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    });
                });
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
            else {
                out.println("Command not found.");
            }
        }
/*        try {
            askToken("https://oauth.vk.com/authorize?client_id=52502099&display=page&redirect_uri=https://oauth.vk.com/blank.html&scope=friends,video,groups&response_type=token&v=5.59");
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }*/

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
