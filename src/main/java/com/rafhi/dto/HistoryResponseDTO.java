package com.rafhi.dto;

import java.time.LocalDateTime;

public class HistoryResponseDTO {
    public Long id;
    public String nomorBA;
    public String jenisBeritaAcara;
    public String judulPekerjaan;
    public LocalDateTime generationTimestamp;

    // Constructor untuk mempermudah konversi
    public HistoryResponseDTO(Long id, String nomorBA, String jenis, String judul, LocalDateTime timestamp) {
        this.id = id;
        this.nomorBA = nomorBA;
        this.jenisBeritaAcara = jenis;
        this.judulPekerjaan = judul;
        this.generationTimestamp = timestamp;
    }
}