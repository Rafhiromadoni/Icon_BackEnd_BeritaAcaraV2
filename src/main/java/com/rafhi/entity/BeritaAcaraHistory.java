package com.rafhi.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import java.time.LocalDateTime;

@Entity
public class BeritaAcaraHistory extends PanacheEntity {

    public String nomorBA;
    public String jenisBeritaAcara;
    public String judulPekerjaan;
    public LocalDateTime generationTimestamp;

    public String username; // Menyimpan username yang membuat berita acara

    @Column(columnDefinition = "TEXT")
    public String requestJson; // Menyimpan seluruh data request sebagai JSON

    @Lob
    public byte[] fileContent; // Menyimpan file .docx sebagai blob

}