package net.opendasharchive.openarchive.services.dropbox

import android.content.Context
import com.dropbox.core.NetworkIOException
import com.dropbox.core.RetryException
import com.dropbox.core.v2.DbxClientV2
import com.dropbox.core.v2.files.CommitInfo
import com.dropbox.core.v2.files.CreateFolderErrorException
import com.dropbox.core.v2.files.FileMetadata
import com.dropbox.core.v2.files.UploadSessionAppendErrorException
import com.dropbox.core.v2.files.UploadSessionCursor
import com.dropbox.core.v2.files.UploadSessionFinishErrorException
import com.dropbox.core.v2.files.WriteMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import net.opendasharchive.openarchive.application.services.Conduit
import net.opendasharchive.openarchive.db.Media
import net.opendasharchive.openarchive.services.SaveClient

class DropboxConduit(media: Media, context: Context) : Conduit(media, context) {

    companion object {
        const val NAME = "Dropbox"
        const val HOST = "dropbox.com"
        const val MAX_RETRIES = 5
    }

    private lateinit var mClient: DbxClientV2

    override suspend fun upload(): Boolean {
        val accessToken = mMedia.space?.password ?: return false
        val path = getPath() ?: return false

        mClient = SaveClient.getDropbox(mContext, accessToken)

        val fileName = getUploadFileName(mMedia)

        sanitize()

        var result: FileMetadata? = null

        try {
            createFolders(null, path)

            uploadMetadata(path, fileName)

            if (mCancelled) throw Exception("Cancelled")

            val destination = construct(path, fileName)

            if (mMedia.contentLength > CHUNK_FILESIZE_THRESHOLD) {
                result = uploadChunked(destination)
            } else {
                execute {
                    mMedia.file.inputStream().use { inputStream ->
                        result = mClient.files()
                            .uploadBuilder(destination)
                            .withMode(WriteMode.OVERWRITE)
                            .withClientModified(mMedia.createDate)
                            .uploadAndFinish(inputStream)
                    }
                }
            }
        } catch (e: Throwable) {
            jobFailed(e)

            return false
        }

        if (result == null) {
            jobFailed(Exception("Empty result"))

            return false
        }

        mMedia.serverUrl = result!!.pathDisplay
        jobSucceeded()

        return true
    }

    override suspend fun createFolder(url: String) {
        execute {
            try {
                mClient.files().createFolderV2(url)
            } catch (e: CreateFolderErrorException) {
                // Ignore. Already existing.
            }
        }
    }

    private suspend fun uploadMetadata(path: List<String>, fileName: String) {
        val metadata = getMetadata()

        if (mCancelled) throw java.lang.Exception("Cancelled")

        execute {
            metadata.byteInputStream().use {
                mClient.files()
                    .uploadBuilder(construct(path, "$fileName.meta.json"))
                    .withMode(WriteMode.OVERWRITE)
                    .uploadAndFinish(it)
            }
        }

        for (file in getProof()) {
            if (mCancelled) throw java.lang.Exception("Cancelled")

            execute {
                file.inputStream().use {
                    mClient.files()
                        .uploadBuilder(construct(path, file.name))
                        .withMode(WriteMode.OVERWRITE)
                        .uploadAndFinish(it)
                }
            }
        }
    }

    private suspend fun uploadChunked(destination: String): FileMetadata? {
        var result: FileMetadata? = null

        mMedia.file.inputStream().use { inputStream ->
            // Write first chunk, get upload session ID.

            val sessionId = mClient.files().uploadSessionStart()
                .uploadAndFinish(inputStream, CHUNK_SIZE)
                .sessionId

            var offset = CHUNK_SIZE
            jobProgress(offset)

            // Write middle chunks.
            while (mMedia.contentLength - offset > CHUNK_SIZE) {
                execute(offset) { skip ->
                    if (skip != 0L) {
                        withContext(Dispatchers.IO) {
                            offset += inputStream.skip(skip)
                        }
                    }

                    val cursor = UploadSessionCursor(sessionId, offset)

                    mClient.files().uploadSessionAppendV2(cursor)
                        .uploadAndFinish(inputStream, CHUNK_SIZE)
                }

                offset += CHUNK_SIZE
                jobProgress(offset)
            }

            // Write last chunk and make file available.
            val info = CommitInfo.newBuilder(destination)
                .withMode(WriteMode.OVERWRITE)
                .withClientModified(mMedia.createDate)
                .build()

            execute(offset) { skip ->
                if (skip != 0L) {
                    withContext(Dispatchers.IO) {
                        offset += inputStream.skip(skip)
                    }
                }

                val remaining = mMedia.contentLength - offset

                result = mClient.files()
                    .uploadSessionFinish(UploadSessionCursor(sessionId, offset), info)
                    .uploadAndFinish(inputStream, remaining)
            }
        }

        return result
    }

    /**
     * method for retrying the `body` code block based on drop box exceptions
     */
    private suspend fun execute(offset: Long? = null, body: suspend (skip: Long) -> Unit) {
        var skip = 0L
        var retries = 0

        while (true) {
            try {
                body(skip)

                break
            } catch (e: RetryException) {
                if (retries < MAX_RETRIES) {
                    delay(e.backoffMillis)
                } else {
                    throw e
                }
            } catch (e: UploadSessionAppendErrorException) {
                if (offset != null && retries < MAX_RETRIES && e.errorValue.isIncorrectOffset) {
                    skip = e.errorValue.incorrectOffsetValue.correctOffset - offset
                } else {
                    throw e
                }
            } catch (e: UploadSessionFinishErrorException) {
                if (offset != null && retries < MAX_RETRIES
                    && e.errorValue.isLookupFailed
                    && e.errorValue.lookupFailedValue.isIncorrectOffset
                ) {
                    skip =
                        e.errorValue.lookupFailedValue.incorrectOffsetValue.correctOffset - offset
                } else {
                    throw e
                }
            } catch (e: NetworkIOException) {
                if (retries < MAX_RETRIES) {
                    // Ignore network problems. We try up to 5 times.
                    // When doing chunking, we just try to send the next chunk.
                    // Dropbox will tell us, if that next chunk's offset was wrong
                    // and we will send it one more time again with the fixed offset.
                } else {
                    throw e
                }
            }

            retries++
        }
    }
}
