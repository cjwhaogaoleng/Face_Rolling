package com.example.face_rolling.ui.postLog.person

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.example.face_rolling.MyViewModel
import com.example.face_rolling.bean.PhotoBean
import com.example.face_rolling.bean.RecognizeBean
import com.example.face_rolling.data.User.Companion.Me
import com.example.face_rolling.network.NetworkRequest
import com.example.face_rolling.util.ToastUtils

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.log

const val FACE_RECOGNIZE = 1
const val AVATAR_UPLOAD = 2
const val NONE = 3

@Composable
fun ImageSelectionScreen(viewModel: MyViewModel) {

    val context = LocalContext.current
    val toastUtils = ToastUtils.getInstance(context)

//    val toastUtils = ToastUtils.getInstance(context)

    val takePicture =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val imageFile: File = File(currentPhotoPath.toString())
                // 图片已经拍摄，处理图片
                // 这里可以将图片文件传给后端


                upImage(context, viewModel, imageFile)
            }
        }

    val pickImage =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                uri?.let {
                    val imageFile = File(getRealPathFromUri(context, it))
                    // 从相册选择图片成功，处理图片
                    // 这里可以将图片文件传给后端

                    val requestBody = RequestBody.create("image/jpg".toMediaTypeOrNull(), imageFile)

                    upImage(context, viewModel, imageFile)
                }
            }
        }

    Column {

        Button(onClick = {
            takePicture.launch(createImageCaptureIntent(context))
        }) {
            Text("拍照")
        }

        Button(onClick = {
            pickImage.launch(
                Intent(
                    Intent.ACTION_PICK,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                )
            )
        }) {
            toastUtils.show("no")
            Text("选择照片")
        }
    }
}

private var currentPhotoPath: String? = null


private fun createImageCaptureIntent(context: Context): Intent {
    val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
    val imageFileName = "JPEG_${timeStamp}_"

    val storageDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
    val imageFile = File.createTempFile(
        imageFileName, /* prefix */
        ".jpg", /* suffix */
        storageDir /* directory */
    )
    currentPhotoPath = imageFile.absolutePath

    val photoURI: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            imageFile
        )
    } else {
        Uri.fromFile(imageFile)
    }

    return Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
        putExtra(MediaStore.EXTRA_OUTPUT, photoURI)
    }
}

private fun getRealPathFromUri(context: Context, uri: Uri): String? {
    val projection = arrayOf(MediaStore.Images.Media.DATA)
    val cursor = context.contentResolver.query(uri, projection, null, null, null)
    return cursor?.use {
        val columnIndex = it.getColumnIndexOrThrow(MediaStore.Images.Media.DATA)
        it.moveToFirst()
        it.getString(columnIndex)
    }
}

//        AsyncImage(
//            model =
//            if (currentPhotoPath != null) Uri.parse(currentPhotoPath) else {
////                "https://ts1.cn.mm.bing.net/th/id/R-C.23034dbcaded6ab4169b9514f76f51b5?rik=mSGADwV9o%2fteUA&riu=http%3a%2f%2fpic.bizhi360.com%2fbbpic%2f40%2f9640_1.jpg&ehk=RYei4n5qyNCPVysJmE2a3WhxSOXqGQMGJcvWBmFyfdg%3d&risl=&pid=ImgRaw&r=0"
//                "${BASE_URL}images/158.png"
//            },
//            contentDescription = null,
//            modifier = Modifier
//                .width(48.dp)
//                .height(48.dp)
//                .clip(CircleShape)
//                .placeholder(
//                    visible = false,
//                    color = Color(231, 234, 239, 255),
//                    highlight = PlaceholderHighlight.shimmer(),
//                ),
//            contentScale = ContentScale.Crop,
//        )

fun upImage(context: Context, viewModel: MyViewModel, imageFile: File) {
    val toastUtils = ToastUtils.getInstance(context)
    if (viewModel.upImageMode == FACE_RECOGNIZE) {
        val requestBody = RequestBody.create("image/jpg".toMediaTypeOrNull(), imageFile)
        val body = MultipartBody.Part.createFormData(
            "together_image",
            imageFile.name,
            requestBody
        )

        NetworkRequest.faceRecognize(body, viewModel.recTeamID)
            .enqueue(object : Callback<RecognizeBean> {
                override fun onResponse(
                    call: Call<RecognizeBean>,
                    response: Response<RecognizeBean>
                ) {
                    Log.d("tag", "onResponse: ${response}")
                    if (response.isSuccessful) {
                        val string = response.body()
                        Log.d("tag", "onResponse: ")
                        if (string!!.data != null) {
                            if (string!!.status == 200) {
                                toastUtils.show("上传成功")
                            } else if (string!!.status == 404) {
                                toastUtils.show("没有人头信息")

                            }
                            viewModel.userAbsentData = string.data!!

                        }

                    } else {
                        toastUtils.show("上传失败")
                    }
                }

                override fun onFailure(call: Call<RecognizeBean>, t: Throwable) {
                    toastUtils.show("网络请求失败")

                }
            })
    } else {
        val requestBody = RequestBody.create("image/jpg".toMediaTypeOrNull(), imageFile)
        val body = MultipartBody.Part.createFormData(
            "photo",
            imageFile.name,
            requestBody
        )
        val call = NetworkRequest.connectImageToPerson(Me.id, body)
        call.enqueue(object : Callback<PhotoBean> {
            override fun onResponse(call: Call<PhotoBean>, response: Response<PhotoBean>) {
                if (response.isSuccessful) {

                    val string = response.body()
                    Log.d("tag", "onResponse: " + string.toString())
                    toastUtils.show("上传成功")

                } else {
                    toastUtils.show("上传失败")
                }
            }

            override fun onFailure(call: Call<PhotoBean>, t: Throwable) {
                toastUtils.show("网络请求失败")

            }

        })
    }
}