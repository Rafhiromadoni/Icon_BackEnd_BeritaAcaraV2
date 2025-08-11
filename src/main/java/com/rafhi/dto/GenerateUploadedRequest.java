package com.rafhi.dto;

import java.util.Map;

/**
 * DTO untuk menerima JSON body pada endpoint /generate-uploaded
 */
public class GenerateUploadedRequest {
    /** Path file template yang sudah di-upload, dikirim front-end */
    public String templatePath;

    /** Map placeholder → nilai yang diisi user */
    public Map<String, String> data;

    // Opsional: getter & setter jika framework-mu butuh
    public String getTemplatePath() { return templatePath; }
    public void setTemplatePath(String templatePath) { this.templatePath = templatePath; }

    public Map<String,String> getData() { return data; }
    public void setData(Map<String,String> data) { this.data = data; }
}
