package com.example

import android.net.Uri
import android.os.Bundle
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.example.data.AppDatabase
import com.example.data.ReportEntity
import com.example.data.ReportRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// --- Models ---
data class ReportData(
    val title: String = "",
    val location: String = "",
    val date: String = "",
    val description: String = "",
    val imageUri: Uri? = null,
    val videoUri: Uri? = null
)

class ReportViewModel(private val repository: ReportRepository) : ViewModel() {
    val allReports = repository.allReports.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _reportData = MutableStateFlow(ReportData())
    val reportData = _reportData.asStateFlow()
    
    private var editingId: Int? = null

    fun saveReport() {
        viewModelScope.launch {
            val current = _reportData.value
            val entity = ReportEntity(
                id = editingId ?: 0,
                title = current.title,
                location = current.location,
                date = current.date,
                description = current.description,
                imageUri = current.imageUri?.toString(),
                videoUri = current.videoUri?.toString()
            )
            if (editingId == null) {
                repository.insert(entity)
            } else {
                repository.update(entity)
            }
            resetForm()
        }
    }

    fun loadReport(entity: ReportEntity) {
        editingId = entity.id
        _reportData.value = ReportData(
            title = entity.title,
            location = entity.location,
            date = entity.date,
            description = entity.description,
            imageUri = entity.imageUri?.let { Uri.parse(it) },
            videoUri = entity.videoUri?.let { Uri.parse(it) }
        )
    }

    fun resetForm() {
        editingId = null
        _reportData.value = ReportData()
    }
    
    fun deleteCurrentReport() {
        editingId?.let { id ->
            viewModelScope.launch {
                val current = _reportData.value
                val entity = ReportEntity(
                    id = id,
                    title = current.title,
                    location = current.location,
                    date = current.date,
                    description = current.description,
                    imageUri = current.imageUri?.toString(),
                    videoUri = current.videoUri?.toString()
                )
                repository.delete(entity)
                resetForm()
            }
        }
    }

    fun updateTitle(newTitle: String) {
        _reportData.value = _reportData.value.copy(title = newTitle)
    }
    fun updateLocation(newLocation: String) {
        _reportData.value = _reportData.value.copy(location = newLocation)
    }
    fun updateDate(newDate: String) {
        _reportData.value = _reportData.value.copy(date = newDate)
    }
    fun updateDescription(newDescription: String) {
        _reportData.value = _reportData.value.copy(description = newDescription)
    }
    fun updateImage(uri: Uri?) {
        _reportData.value = _reportData.value.copy(imageUri = uri)
    }
    fun updateVideo(uri: Uri?) {
        _reportData.value = _reportData.value.copy(videoUri = uri)
    }
}

class ReportViewModelFactory(private val repository: ReportRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ReportViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ReportViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

// --- MainActivity ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = AppDatabase.getDatabase(applicationContext)
        val repository = ReportRepository(db.reportDao())
        val viewModelFactory = ReportViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, viewModelFactory)[ReportViewModel::class.java]

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val navController = rememberNavController()
                NavHost(navController = navController, startDestination = "home") {
                    composable("home") {
                        HomeScreen(viewModel, navController)
                    }
                    composable("form") {
                        FormScreen(viewModel, navController)
                    }
                    composable("slide") {
                        SlideScreen(viewModel, navController)
                    }
                    composable("camera/{mode}") { backStackEntry ->
                        val mode = backStackEntry.arguments?.getString("mode") ?: "photo"
                        CameraScreen(
                            mode = mode,
                            onMediaCaptured = { uri ->
                                if (mode == "photo") {
                                    viewModel.updateImage(uri)
                                } else {
                                    viewModel.updateVideo(uri)
                                }
                                navController.popBackStack()
                            },
                            onClose = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }
}

// --- Screens ---

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(viewModel: ReportViewModel, navController: NavController) {
    val reports by viewModel.allReports.collectAsStateWithLifecycle()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Daftar Laporan") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                viewModel.resetForm()
                navController.navigate("form") 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Tambah Laporan")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (reports.isEmpty()) {
                item {
                    Text(
                        text = "Belum ada laporan kegiatan.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            items(reports) { report ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        viewModel.loadReport(report)
                        navController.navigate("form")
                    }
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = report.title.ifEmpty { "Tanpa Judul" }, 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (report.location.isNotEmpty() || report.date.isNotEmpty()) {
                            val locText = listOf(report.location, report.date).filter { it.isNotEmpty() }.joinToString(" - ")
                            Text(locText, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormScreen(viewModel: ReportViewModel, navController: NavController) {
    val reportData by viewModel.reportData.collectAsState()
    
    // Launchers for picking media
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.updateImage(it) } }
    )
    
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
        onResult = { uri -> uri?.let { viewModel.updateVideo(it) } }
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Buat Laporan Kegiatan") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.deleteCurrentReport()
                        navController.popBackStack()
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Hapus Laporan", tint = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { 
                        viewModel.saveReport()
                        navController.popBackStack()
                    }) {
                        Text("Simpan", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { navController.navigate("slide") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Filled.PlayCircleFilled, contentDescription = "Preview Slide") },
                text = { Text("Lihat Slide") }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Detail Laporan",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            OutlinedTextField(
                value = reportData.title,
                onValueChange = { viewModel.updateTitle(it) },
                label = { Text("Judul Kegiatan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = reportData.location,
                onValueChange = { viewModel.updateLocation(it) },
                label = { Text("Lokasi Kegiatan") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.LocationOn, contentDescription = null) }
            )

            var showDatePicker by remember { mutableStateOf(false) }
            val datePickerState = rememberDatePickerState()

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                val date = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(millis))
                                viewModel.updateDate(date)
                            }
                            showDatePicker = false
                        }) {
                            Text("Pilih")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) {
                            Text("Batal")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            OutlinedTextField(
                value = reportData.date,
                onValueChange = { },
                label = { Text("Tanggal Kegiatan") },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showDatePicker = true },
                singleLine = true,
                readOnly = true,
                enabled = false,
                colors = OutlinedTextFieldDefaults.colors(
                    disabledTextColor = MaterialTheme.colorScheme.onSurface,
                    disabledBorderColor = MaterialTheme.colorScheme.outline,
                    disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )

            OutlinedTextField(
                value = reportData.description,
                onValueChange = { viewModel.updateDescription(it) },
                label = { Text("Deskripsi Singkat") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Text(
                text = "Media Presentasi",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Image Box
                MediaPickerBox(
                    modifier = Modifier.weight(1f),
                    title = "Foto Kegiatan",
                    icon = Icons.Default.Image,
                    hasMedia = reportData.imageUri != null,
                    onGalleryClick = {
                        imagePicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onCameraClick = {
                        navController.navigate("camera/photo")
                    }
                )

                // Video Box
                MediaPickerBox(
                    modifier = Modifier.weight(1f),
                    title = "Video Kegiatan",
                    icon = Icons.Default.VideoFile,
                    hasMedia = reportData.videoUri != null,
                    onGalleryClick = {
                        videoPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    onCameraClick = {
                        navController.navigate("camera/video")
                    }
                )
            }
            
            Spacer(modifier = Modifier.height(80.dp)) // Space for FAB
        }
    }
}

@Composable
fun MediaPickerBox(
    modifier: Modifier = Modifier,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    hasMedia: Boolean,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Pilih Sumber Media") },
            text = { Text("Ambil dari Kamera atau pilih dari Galeri?") },
            confirmButton = {
                TextButton(onClick = {
                    showDialog = false
                    onCameraClick()
                }) {
                    Text("Kamera")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDialog = false
                    onGalleryClick()
                }) {
                    Text("Galeri")
                }
            }
        )
    }

    Card(
        modifier = modifier
            .height(140.dp)
            .clickable { showDialog = true },
        colors = CardDefaults.cardColors(
            containerColor = if (hasMedia) MaterialTheme.colorScheme.secondaryContainer 
                             else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (!hasMedia) null else androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = if (hasMedia) Icons.Default.AddPhotoAlternate else icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = if (hasMedia) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (hasMedia) "Ganti Media" else title,
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlideScreen(viewModel: ReportViewModel, navController: NavController) {
    val reportData by viewModel.reportData.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isExporting by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mode Presentasi") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    isExporting = true
                    coroutineScope.launch {
                        val file = PptxGenerator.generatePptx(
                            context = context,
                            title = reportData.title,
                            location = reportData.location,
                            date = reportData.date,
                            description = reportData.description,
                            imageUri = reportData.imageUri,
                            videoUri = reportData.videoUri
                        )
                        isExporting = false
                        if (file != null) {
                            android.widget.Toast.makeText(context, "Berhasil diekspor ke: ${file.name}", android.widget.Toast.LENGTH_LONG).show()
                            
                            // Share intent
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(shareIntent, "Bagikan Presentasi PPTX"))
                            
                        } else {
                            android.widget.Toast.makeText(context, "Gagal mengekspor PPTX", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                containerColor = MaterialTheme.colorScheme.tertiary,
                contentColor = MaterialTheme.colorScheme.onTertiary,
                icon = { 
                    if (isExporting) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onTertiary)
                    } else {
                        Icon(Icons.Default.VideoFile, contentDescription = "Ekspor PPTX") 
                    }
                },
                text = { Text(if (isExporting) "Mengekspor..." else "Ekspor PPTX") }
            )
        },
        containerColor = Color(0xFF1E1E1E) // Dark theme for PPT look
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            // The "Slide" Canvas
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.90f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {
                    // Slide Header
                    Text(
                        text = reportData.title.ifEmpty { "Judul Laporan Kegiatan" },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A1A1A)
                    )
                    if (reportData.location.isNotEmpty() || reportData.date.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        val locText = listOf(reportData.location, reportData.date).filter { it.isNotEmpty() }.joinToString(" - ")
                        Text(
                            text = locText,
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.Gray
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFE0E0E0))
                    Spacer(modifier = Modifier.height(16.dp))

                    // Media Grid (Side by side if landscape, or top-bottom if portrait slide. We'll do top-bottom for mobile view)
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Image section
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFF5F5F5)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (reportData.imageUri != null) {
                                AsyncImage(
                                    model = reportData.imageUri,
                                    contentDescription = "Foto Kegiatan",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Text("Tidak ada foto", color = Color.Gray)
                            }
                        }

                        // Video section
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF000000)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (reportData.videoUri != null) {
                                VideoPlayer(uri = reportData.videoUri)
                            } else {
                                Text("Tidak ada video", color = Color.Gray)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Slide Footer (Description)
                    Text(
                        text = reportData.description.ifEmpty { "Deskripsi kegiatan akan tampil di sini." },
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFF333333),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun VideoPlayer(uri: Uri?) {
    if (uri == null) return
    val context = LocalContext.current
    
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            VideoView(ctx).apply {
                setVideoURI(uri)
                val mediaController = MediaController(ctx)
                mediaController.setAnchorView(this)
                setMediaController(mediaController)
                
                setOnPreparedListener { mp ->
                    mp.isLooping = true
                    // Start playing automatically
                    start()
                }
            }
        },
        update = { view ->
            view.setVideoURI(uri)
        }
    )
}
