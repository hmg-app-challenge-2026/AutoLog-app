package kr.htaxlog.relay.receipt

import io.minio.BucketExistsArgs
import io.minio.GetPresignedObjectUrlArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.minio.RemoveObjectArgs
import io.minio.http.Method
import kr.htaxlog.relay.common.RelayProperties
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/** 영수증 원본·썸네일 저장소. 테스트에서 가짜로 바꿀 수 있게 인터페이스로 둔다. */
interface ReceiptStorage {
    fun put(key: String, bytes: ByteArray, contentType: String)

    /** 차가 썸네일을 받을 서명 URL. */
    fun signedUrl(key: String): String

    fun delete(key: String)
}

/**
 * MinIO(S3 호환) 저장소.
 * 업로드는 내부 주소([RelayProperties.Storage.endpoint]), 서명 URL은 차가 접근할 공개 주소(publicEndpoint)로 만든다.
 */
@Component
class MinioReceiptStorage(private val props: RelayProperties) : ReceiptStorage {
    private val s = props.storage
    private val internal = MinioClient.builder().endpoint(s.endpoint).credentials(s.accessKey, s.secretKey).build()

    // region을 고정하면 서명할 때 공개 주소로 네트워크 요청을 보내지 않는다.
    private val public =
        MinioClient
            .builder()
            .endpoint(s.publicEndpoint)
            .region(REGION)
            .credentials(s.accessKey, s.secretKey)
            .build()

    @Volatile private var bucketReady = false

    override fun put(key: String, bytes: ByteArray, contentType: String) {
        ensureBucket()
        internal.putObject(
            PutObjectArgs
                .builder()
                .bucket(s.bucket)
                .`object`(key)
                .stream(ByteArrayInputStream(bytes), bytes.size.toLong(), -1)
                .contentType(contentType)
                .build(),
        )
    }

    override fun signedUrl(key: String): String = public.getPresignedObjectUrl(
        GetPresignedObjectUrlArgs
            .builder()
            .method(Method.GET)
            .bucket(s.bucket)
            .`object`(key)
            .expiry(s.signedUrlDays, TimeUnit.DAYS)
            .build(),
    )

    override fun delete(key: String) {
        internal.removeObject(RemoveObjectArgs.builder().bucket(s.bucket).`object`(key).build())
    }

    private fun ensureBucket() {
        if (bucketReady) return
        if (!internal.bucketExists(BucketExistsArgs.builder().bucket(s.bucket).build())) {
            internal.makeBucket(MakeBucketArgs.builder().bucket(s.bucket).build())
        }
        bucketReady = true
    }

    private companion object {
        const val REGION = "us-east-1"
    }
}
