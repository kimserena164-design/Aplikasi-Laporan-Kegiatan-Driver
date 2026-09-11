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
        imageUri: Uri?,
        videoUri: Uri?
    ): File? = withContext(Dispatchers.IO) {
        try {
            val ppt = XMLSlideShow()
            val slide = ppt.createSlide()
            
            // Add Title
            val titleShape = slide.createTextBox()
            titleShape.setAnchor(java.awt.Rectangle(50, 50, 600, 50))
            titleShape.addNewTextParagraph().addNewTextRun().apply {
                setText(title.ifEmpty { "Laporan Kegiatan" })
                setFontSize(32.0)
                setBold(true)
            }

            // Add Location & Description
            val textShape = slide.createTextBox()
            textShape.setAnchor(java.awt.Rectangle(50, 120, 600, 100))
            val p1 = textShape.addNewTextParagraph().addNewTextRun()
            
            val locText = listOf(location, date).filter { it.isNotEmpty() }.joinToString(" - ")
            p1.setText("Lokasi/Tanggal: $locText\n\n$description")
            p1.setFontSize(18.0)

            // Add Image if exists
            var yOffset = 250
            if (imageUri != null) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(imageUri)
                    val bytes = inputStream?.readBytes()
                    inputStream?.close()
                    if (bytes != null) {
                        val pictureData = ppt.addPicture(bytes, org.apache.poi.sl.usermodel.PictureData.PictureType.JPEG)
                        val pictureShape = slide.createPicture(pictureData)
                        pictureShape.setAnchor(java.awt.Rectangle(50, yOffset, 300, 200))
                        yOffset += 220
                    }
                } catch (e: Exception) {
                    Log.e("PptxGenerator", "Failed to add image", e)
                }
            }

            // Add Video Path if exists
            if (videoUri != null) {
                val videoPathShape = slide.createTextBox()
                videoPathShape.setAnchor(java.awt.Rectangle(50, yOffset, 600, 50))
                val videoRun = videoPathShape.addNewTextParagraph().addNewTextRun()
                
                val fileName = getFileName(context, videoUri)
                videoRun.setText("Video terlampir: $fileName\nPath: $videoUri")
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
