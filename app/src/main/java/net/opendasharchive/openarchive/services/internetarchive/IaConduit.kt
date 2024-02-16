package net.opendasharchive.openarchive.services.internetarchive

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import net.opendasharchive.openarchive.R
import net.opendasharchive.openarchive.application.services.Conduit
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.services.SaveClient
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okio.BufferedSink
import java.io.File
import java.io.IOException

class IaConduit(media: Media, context: Context) : Conduit(media, context) {


    companion object {
        const val ARCHIVE_BASE_URL = "https://archive.org/"
        const val NAME = "Internet Archive"

        const val ARCHIVE_API_ENDPOINT = "https://s3.us.archive.org"
        private const val ARCHIVE_DETAILS_ENDPOINT = "https://archive.org/details/"

        private fun getSlug(title: String): String {
            return title.replace("[^A-Za-z\\d]".toRegex(), "-")
        }
    }

    override suspend fun upload(): Boolean {
        sanitize()

        try {
            val mediaUri = mMedia.originalFilePath
            val mimeType = mMedia.mimeType

            // TODO this should make sure we aren't accidentally using one of archive.org's metadata fields by accident
            val slug = getSlug(mMedia.title)
            var basePath = "$slug-${Util.RandomString(4).nextString()}"
            val url = "$ARCHIVE_API_ENDPOINT/$basePath/" + getUploadFileName(mMedia, true)
            val requestBody =
                getRequestBody(mMedia, mediaUri, mimeType.toMediaTypeOrNull(), basePath)

            put(url, requestBody, mainHeader())

            /// Upload metadata
            basePath = "$slug-${Util.RandomString(4).nextString()}"

            uploadMetaData(Gson().toJson(mMedia), basePath, getUploadFileName(mMedia, true))

            /// Upload ProofMode metadata, if enabled and successfully created.
            for (file in getProof()) {
                uploadProofFiles(file, basePath)
            }

            return true
        } catch (e: Exception) {
            jobFailed(e)
        }

        return false
    }

    override suspend fun createFolder(url: String) {
        // Ignored. Not used here.
    }

    @Throws(IOException::class)
    private suspend fun uploadMetaData(content: String, basePath: String, fileName: String) {
        val requestBody = object : RequestBody() {
            override fun contentType(): MediaType? {
                return "texts".toMediaTypeOrNull()
            }

            override fun writeTo(sink: BufferedSink) {
                sink.writeString(content, Charsets.UTF_8)
            }
        }

        put(
            "$ARCHIVE_API_ENDPOINT/$basePath/$fileName.meta.json",
            requestBody,
            metadataHeader()
        )
    }

    /// upload proof mode
    @Throws(IOException::class)
    private suspend fun uploadProofFiles(uploadFile: File, basePath: String) {
        val requestBody = getRequestBodyMetaData(
            uploadFile,
            Uri.fromFile(uploadFile).toString(),
            "texts".toMediaTypeOrNull()
        )

        put(
            "$ARCHIVE_API_ENDPOINT/$basePath/${uploadFile.name}",
            requestBody,
            metadataHeader()
        )
    }

    private fun getRequestBody(
        media: Media,
        mediaUri: String?,
        mediaType: MediaType?,
        basePath: String
    ): RequestBody {
        return RequestBodyUtil.create(
            mContext.contentResolver,
            Uri.parse(mediaUri),
            media.contentLength,
            mediaType,
            object : RequestListener {
                var lastBytes: Long = 0
                override fun transferred(bytes: Long) {
                    if (bytes > lastBytes) {
                        jobProgress(bytes)
                        lastBytes = bytes
                    }
                }

                override fun continueUpload(): Boolean {
                    return !mCancelled
                }

                override fun transferComplete() {
                    val finalPath = ARCHIVE_DETAILS_ENDPOINT + basePath
                    media.serverUrl = finalPath
                    jobSucceeded()
                }
            })
    }

    /// request body for meta data
    private fun getRequestBodyMetaData(
        media: File,
        mediaUri: String,
        mediaType: MediaType?
    ): RequestBody {
        return RequestBodyUtil.create(
            mContext.contentResolver,
            Uri.parse(mediaUri),
            media.length(),
            mediaType,
            object : RequestListener {
                var lastBytes: Long = 0

                override fun transferred(bytes: Long) {
                    if (bytes > lastBytes) {
                        jobProgress(bytes)
                        lastBytes = bytes
                    }
                }

                override fun continueUpload(): Boolean {
                    return !mCancelled
                }

                override fun transferComplete() {
                    jobSucceeded()
                }
            })
    }

    private fun mainHeader(): Headers {
        val builder = Headers.Builder()
            .add("Accept", "*/*")
            .add("x-archive-auto-make-bucket", "1")
            .add("x-amz-auto-make-bucket", "1")
            .add("x-archive-interactive-priority", "1")
            .add("x-archive-meta-language", "eng") // FIXME set based on locale or selected.
            .add("Authorization", "LOW " + mMedia.space?.username + ":" + mMedia.space?.password)

        val author = mMedia.author
        if (author.isNotEmpty()) {
            builder.add("x-archive-meta-author", author)
        }

        val collection = when {
            mMedia.mimeType.startsWith("video") -> "opensource_movies"
            mMedia.mimeType.startsWith("audio") -> "opensource_audio"
            else -> "opensource_media"
        }
        builder.add("x-archive-meta-collection", collection)

        if (mMedia.mimeType.isNotEmpty()) {
            val mediaType = when {
                mMedia.mimeType.startsWith("image") -> "image"
                mMedia.mimeType.startsWith("video") -> "movies"
                mMedia.mimeType.startsWith("audio") -> "audio"
                else -> "data"
            }
            builder.add("x-archive-meta-mediatype", mediaType)
        }

        if (mMedia.location.isNotEmpty()) {
            builder.add("x-archive-meta-location", mMedia.location)
        }

        if (mMedia.tags.isNotEmpty()) {
            val tags = mMedia.tagSet
            tags.add(mContext.getString(R.string.default_tags))
            mMedia.tagSet = tags

            builder.add("x-archive-meta-subject", mMedia.tags)
        }

        if (mMedia.description.isNotEmpty()) {
            builder.add("x-archive-meta-description", mMedia.description)
        }

        if (mMedia.title.isNotEmpty()) {
            builder.add("x-archive-meta-title", mMedia.title)
        }

        var licenseUrl = mMedia.licenseUrl

        if (licenseUrl.isNullOrEmpty()) {
            licenseUrl = "https://creativecommons.org/licenses/by/4.0/"
        }

        builder.add("x-archive-meta-licenseurl", licenseUrl)

        return builder.build()
    }

    /// headers for meta-data and proof mode
    private fun metadataHeader(): Headers {
        return Headers.Builder()
            .add("x-amz-auto-make-bucket", "1")
            .add("x-archive-meta-language", "eng") // FIXME set based on locale or selected
            .add("Authorization", "LOW " + mMedia.space?.username + ":" + mMedia.space?.password)
            .add("x-archive-meta-mediatype", "texts")
            .add("x-archive-meta-collection", "opensource")
            .build()
    }

    @Throws(Exception::class)
    private suspend fun put(url: String, requestBody: RequestBody, headers: Headers) {
        val request = Request.Builder()
            .url(url)
            .put(requestBody)
            .headers(headers)
            .build()

        execute(request)
    }

    @Throws(Exception::class)
    private suspend fun execute(request: Request) {
        SaveClient.get(mContext)
            .newCall(request)
            .enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    jobFailed(e)
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        jobSucceeded()
                    } else {
                        jobFailed(Exception("${response.code} ${response.message}"))
                    }
                }
            })
    }
}
