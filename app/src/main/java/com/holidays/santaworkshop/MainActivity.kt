package com.holidays.santaworkshop

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.functions.ktx.functions
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.holidays.santaworkshop.ui.theme.SantaWorkShopTheme
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SantaWorkShopTheme {
                Home(
                    takePictureOnClick = {
                        dispatchTakePictureIntent()
                    }
                )

            }
        }
    }


    val IMAGE_CAPTURE_REQUEST_CODE = 1000
    private val TAG = "SantaWorkshop"
    lateinit var currentPhotoPath: String

    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Ensure that there's a camera activity to handle the intent
            takePictureIntent.resolveActivity(packageManager).also {
                // Create the File where the photo should go
                val photoFile: File? = try {
                    createImageFile()
                } catch (ex: IOException) {
                    // Error occurred while creating the File
                    Log.i(TAG, "error during creation of file")
                    null
                }
                // Continue only if the File was successfully created
                photoFile?.also {
                    val photoURI: Uri = FileProvider.getUriForFile(
                        this,
                        "com.holidays.santaworkshop.fileprovider",
                        it
                    )
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
                    startActivityForResult(takePictureIntent, IMAGE_CAPTURE_REQUEST_CODE)
                }
            }
        }
    }


    val storageRef = Firebase.storage.reference

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == IMAGE_CAPTURE_REQUEST_CODE && resultCode == RESULT_OK) {
            var file = Uri.fromFile(File(currentPhotoPath))
            val imageRef = storageRef.child("images/${file.lastPathSegment}")
            val uploadTask = imageRef.putFile(file)
// Register observers to listen for when the download is done or if it fails
            uploadTask.addOnFailureListener {
                // Handle unsuccessful uploads
                Log.i(TAG, "onFailure")
            }.addOnSuccessListener { taskSnapshot ->
                // taskSnapshot.metadata contains file metadata such as size, content-type, etc.
                Log.i(TAG, "onSuccess")

            }
        }
        super.onActivityResult(requestCode, resultCode, data)
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        // Create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        ).apply {
            // Save a file: path for use with ACTION_VIEW intents
            currentPhotoPath = absolutePath
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun Home(takePictureOnClick: () -> Unit) {
    val refreshScope = rememberCoroutineScope()
    var items by remember {
        mutableStateOf(listOf<String>())
    }
    var refreshing by remember { mutableStateOf(false) }

    fun refresh() = refreshScope.launch {
        refreshing = true
        val task = Firebase.functions
            .getHttpsCallable("getAllImages")
            .call()
            .continueWith { task ->
                // This continuation runs on either success or failure, but if the task
                // has failed then result will throw an Exception which will be
                // propagated down.
                val result = task.result?.data as? List<String> ?: emptyList()
                result
            }
        task.addOnCompleteListener {
            items = if (it.isSuccessful) {
                it.result
            } else {
                emptyList()
            }
            refreshing = false
        }

    }


    val pullRefreshState = rememberPullRefreshState(refreshing, ::refresh)
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text(text = "Santa Workshop")
            },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    navigationIconContentColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ))
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = takePictureOnClick,
                shape = CircleShape,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                content = {
                    Icon(Icons.Default.Add, "Camera")
                },
                containerColor = MaterialTheme.colorScheme.primary)
        },
        content = {
            // A surface container using the 'background' color from the theme
            Surface(modifier = Modifier
                .fillMaxSize()
                .padding(it),
                color = MaterialTheme.colorScheme.surface) {
                Box(Modifier.pullRefresh(pullRefreshState)) {
                    LazyVerticalGrid(columns = GridCells.Adaptive(minSize = 128.dp),
                        modifier = Modifier.fillMaxSize()) {
                        items(items = items) { item ->
                            Image(painter = rememberAsyncImagePainter(model = item),
                                contentDescription = "",
                                modifier = Modifier.size(128.dp))
                        }
                    }

                    PullRefreshIndicator(refreshing,
                        pullRefreshState,
                        Modifier.align(Alignment.TopCenter))
                }

            }
        })
}
