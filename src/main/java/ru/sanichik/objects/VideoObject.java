package ru.sanichik.objects;

import com.vk.api.sdk.objects.video.VideoFull;

import java.util.Objects;

public class VideoObject {

    private int id;

    private String title;

    private String description;

    public VideoObject(int id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public VideoObject(VideoFull video) {
        this.id = video.getId();
        this.title = video.getTitle();
        this.description = video.getDescription();
    }

    public int getId() {
        return this.id;
    }

    public String getTitle() {
        return this.title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return this.description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VideoObject that = (VideoObject) o;
        return id == that.id; // Сравнение только по id
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

}
