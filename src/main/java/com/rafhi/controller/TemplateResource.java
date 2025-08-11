package com.rafhi.controller;

// --- Jakarta REST annotations (tidak mengimpor jakarta.ws.rs.Path) ---
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher; // we import Paths
import java.util.regex.Pattern;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;

import com.rafhi.dto.GenerateUploadedRequest;
import com.rafhi.dto.TemplateUploadForm;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@jakarta.ws.rs.Path("/api/template")
public class TemplateResource {

    @POST
    @jakarta.ws.rs.Path("/upload")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response uploadTemplate(@MultipartForm TemplateUploadForm form) throws IOException {
        // Simpan stream upload ke temp file
        java.nio.file.Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "berita-acara");
        Files.createDirectories(tempDir);
        java.nio.file.Path tmp = Files.createTempFile(tempDir, "tpl-", ".docx");
        try (InputStream in = form.file) {
            Files.copy(in, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }

        // Ekstrak placeholder baik [foo] maupun ${bar}
        Set<String> placeholders = new HashSet<>();
        Pattern extract = Pattern.compile("\\[(.+?)\\]|\\$\\{(.+?)\\}");
        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(tmp))) {
            // Proses semua paragraf biasa
            for (XWPFParagraph para : doc.getParagraphs()) {
                Matcher m = extract.matcher(para.getText());
                while (m.find()) {
                    String key = m.group(1) != null ? m.group(1) : m.group(2);
                    placeholders.add(key);
                }
            }
            // Proses juga teks di dalam tabel
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph para : cell.getParagraphs()) {
                            Matcher m = extract.matcher(para.getText());
                            while (m.find()) {
                                String key = m.group(1) != null ? m.group(1) : m.group(2);
                                placeholders.add(key);
                            }
                        }
                    }
                }
            }
        }

        // Kembalikan JSON
        Map<String,Object> resp = Map.of(
          "placeholders", List.copyOf(placeholders),
          "templatePath", tmp.toString()
        );
        return Response.ok(resp).build();
    }

    @POST
    @jakarta.ws.rs.Path("/generate-uploaded")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces("application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public Response generateFromUploaded(GenerateUploadedRequest req) throws IOException {
        java.nio.file.Path path = Paths.get(req.templatePath);
        if (!Files.exists(path)) {
            return Response.status(Response.Status.NOT_FOUND)
                        .entity("Template not found: " + req.templatePath)
                        .build();
        }

        try (XWPFDocument doc = new XWPFDocument(Files.newInputStream(path));
            ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // 1) Kumpulkan semua paragraf: dokumen + di dalam tabel
            List<XWPFParagraph> allParas = new ArrayList<>(doc.getParagraphs());
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        allParas.addAll(cell.getParagraphs());
                    }
                }
            }

            // 2) Untuk tiap paragraf, gabung teks semua run → replace → tulis ulang sebagai satu run
            // … setelah mengumpulkan allParas seperti sebelumnya …
            for (XWPFParagraph para : allParas) {
                // 1) Ambil teks lama utuh
                StringBuilder sb = new StringBuilder();
                for (XWPFRun run : para.getRuns()) {
                    String t = run.getText(0);
                    if (t != null) sb.append(t);
                }
                String originalText = sb.toString();

                // 2) Lakukan replace untuk semua placeholder
                String replacedText = originalText;
                for (Map.Entry<String, String> e : req.data.entrySet()) {
                    String key = e.getKey();
                    String val = e.getValue() == null ? "" : e.getValue();
                    replacedText = replacedText
                        .replace("[" + key + "]", val)
                        .replace("${" + key + "}", val);
                }

                // 3) Jika tidak ada perubahan (tidak ada placeholder di paragraf ini), skip
                if (replacedText.equals(originalText)) {
                    continue;
                }

                // 4) Hapus semua run lama (teks dan placeholder) tapi Gambar/logo paragraf lain tidak terpengaruh
                int runCount = para.getRuns().size();
                for (int i = runCount - 1; i >= 0; i--) {
                    para.removeRun(i);
                }

                // 5) Buat satu run baru berisi teks yang sudah diganti
                XWPFRun newRun = para.createRun();
                newRun.setText(replacedText, 0);
            }


            // 3) tulis dokumen hasil generate
            doc.write(out);
            byte[] bytes = out.toByteArray();

            return Response.ok(bytes)
                        .header("Content-Disposition", "attachment; filename=berita_acara.docx")
                        .build();
        }
    }
}