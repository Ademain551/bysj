package com.dlu.mtjbysj.guide;

import java.util.List;

public class GuideCreateArticleRequest {

    private String authorUsername;
    private String title;
    private String content;
    private String coverImageUrl;
    private List<String> imageUrls;
    private List<Long> recommendedItemIds;

    public String getAuthorUsername() {
        return authorUsername;
    }

    public void setAuthorUsername(String authorUsername) {
        this.authorUsername = authorUsername;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public List<String> getImageUrls() {
        return imageUrls;
    }

    public void setImageUrls(List<String> imageUrls) {
        this.imageUrls = imageUrls;
    }

    public List<Long> getRecommendedItemIds() {
        return recommendedItemIds;
    }

    public void setRecommendedItemIds(List<Long> recommendedItemIds) {
        this.recommendedItemIds = recommendedItemIds;
    }
}
