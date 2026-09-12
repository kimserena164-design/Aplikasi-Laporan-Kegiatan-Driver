package com.example

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xslf.usermodel.XMLSlideShow
import org.apache.poi.xslf.usermodel.XSLFTextShape
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object PptxGenerator {

    suspend fun generatePptx(
        context: Context,
        title: String,
        location: String,
        date: String,
        description: String,
        imageUris: List<Uri>,
        videoUri: Uri?
    ): File? = withContext(Dispatchers.IO) {
        try {
            val ppt = XMLSlideShow()
            val slide = ppt.createSlide()
            
            // Layout Configuration
            val pageWidth = ppt.pageSize.width
            val pageHeight = ppt.pageSize.height
            var contentX = 20
            var contentWidth = 680
            
            // Add Background if exists
            val bgFile = File(context.filesDir, "app_bg_image")
            if (bgFile.exists()) {
                try {
                    val bgBytes = bgFile.readBytes()
                    val pictureData = ppt.addPicture(bgBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
                    val bgShape = slide.createPicture(pictureData)
                    bgShape.setAnchor(java.awt.Rectangle(0, 0, pageWidth, pageHeight))
                    
                    // If a background is uploaded, assume it's the requested template which has a green bar on the left.
                    // We need to shift the content to the right (white area).
                    contentX = 230
                    contentWidth = pageWidth - contentX - 20
                } catch (e: Exception) {
                    Log.e("PptxGenerator", "Failed to add background", e)
                }
            }
            
            // Add Logo if exists
            val logoFile = File(context.filesDir, "app_logo_image")
            var titleX = contentX
            if (logoFile.exists()) {
                try {
                    val logoBytes = logoFile.readBytes()
                    val pictureData = ppt.addPicture(logoBytes, org.apache.poi.sl.usermodel.PictureData.PictureType.PNG)
                    val logoShape = slide.createPicture(pictureData)
                    logoShape.setAnchor(java.awt.Rectangle(contentX, 20, 70, 70))
                    titleX = contentX + 80
                } catch (e: Exception) {
                    Log.e("PptxGenerator", "Failed to add logo", e)
                }
            }
            
            // Add Title
            val titleShape = slide.createTextBox()
            titleShape.setAnchor(java.awt.Rectangle(titleX, 20, pageWidth - titleX - 20, 50))
            val titleP = titleShape.addNewTextParagraph()
            titleP.addNewTextRun().apply {
                setText("LAPORAN KEGIATAN")
                setFontSize(28.0)
                setBold(true)
            }

            // Add Details (Judul, Lokasi, Tanggal, Deskripsi)
            val textShape = slide.createTextBox()
            textShape.clearText()
            textShape.setAnchor(java.awt.Rectangle(contentX, 90, contentWidth, 100))
            
            val p1 = textShape.addNewTextParagraph()
            p1.addNewTextRun().apply { setText("Judul Kegiatan: "); setBold(true); setFontSize(14.0) }
            p1.addNewTextRun().apply { setText(title); setFontSize(14.0) }
            
            val p2 = textShape.addNewTextParagraph()
            p2.addNewTextRun().apply { setText("Lokasi Kegiatan: "); setBold(true); setFontSize(14.0) }
            p2.addNewTextRun().apply { setText(location); setFontSize(14.0) }
            
            val p3 = textShape.addNewTextParagraph()
            p3.addNewTextRun().apply { setText("Tanggal Kegiatan: "); setBold(true); setFontSize(14.0) }
            p3.addNewTextRun().apply { setText(date); setFontSize(14.0) }
            
            val p4 = textShape.addNewTextParagraph()
            p4.addNewTextRun().apply { setText("Deskripsi Singkat: "); setBold(true); setFontSize(14.0) }
            p4.addNewTextRun().apply { setText(description); setFontSize(14.0) }

            // Define 4 grid slots based on available width
            val slotW = (contentWidth - 10) / 2
            val slotH = 150
            val row1Y = 220
            val row2Y = 380
            val slots = listOf(
                java.awt.Rectangle(contentX, row1Y, slotW, slotH),
                java.awt.Rectangle(contentX + slotW + 10, row1Y, slotW, slotH),
                java.awt.Rectangle(contentX, row2Y, slotW, slotH),
                java.awt.Rectangle(contentX + slotW + 10, row2Y, slotW, slotH)
            )

            var currentSlotIndex = 0

            // Add Images (Up to 3)
            for (uri in imageUris.take(3)) {
                if (currentSlotIndex >= slots.size) break
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val pictureData = ppt.addPicture(bytes, org.apache.poi.sl.usermodel.PictureData.PictureType.JPEG)
                        val pictureShape = slide.createPicture(pictureData)
                        pictureShape.setAnchor(slots[currentSlotIndex])
                        currentSlotIndex++
                    }
                } catch (e: Exception) {
                    Log.e("PptxGenerator", "Failed to add image", e)
                }
            }

            // Add Video Path if exists
            if (videoUri != null && currentSlotIndex < slots.size) {
                val videoPathShape = slide.createTextBox()
                videoPathShape.setAnchor(slots[currentSlotIndex])
                videoPathShape.setFillColor(java.awt.Color(230, 230, 230))
                val videoRun = videoPathShape.addNewTextParagraph().addNewTextRun()
                
                val fileName = getFileName(context, videoUri)
                videoRun.setText("Video terlampir:\n$fileName\n\n(Tersimpan di path lokal)")
                videoRun.setFontSize(14.0)
            }

            // Save to Downloads folder
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, "Laporan_${System.currentTimeMillis()}.pptx")
            FileOutputStream(file).use { out ->
                ppt.write(out)
            }
            ppt.close()
            
            return@withContext file
        } catch (e: Exception) {
            Log.e("PptxGenerator", "Failed to generate PPTX", e)
            return@withContext null
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index != -1) {
                        result = cursor.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "video.mp4"
    }
}
