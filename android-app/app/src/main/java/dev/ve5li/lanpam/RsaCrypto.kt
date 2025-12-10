package dev.ve5li.lanpam

import android.content.Context
import android.util.Base64
import android.util.Log
import java.io.File
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class RsaCrypto(context: Context) {
    private val keyPair: KeyPair

    companion object {
        private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
        private const val AES_TRANSFORMATION = "AES/ECB/PKCS5Padding"
        private const val KEY_SIZE = 2048
        private const val PRIVATE_KEY_FILE = "pam_private_key.pem"
        private const val PUBLIC_KEY_FILE = "pam_public_key.pem"
    }

    init {
        val privateKeyFile = File(context.filesDir, PRIVATE_KEY_FILE)
        val publicKeyFile = File(context.filesDir, PUBLIC_KEY_FILE)

        keyPair = if (privateKeyFile.exists() && publicKeyFile.exists()) {
            Log.d("RsaCrypto", "Loading existing keypair")
            loadKeyPair(privateKeyFile, publicKeyFile)
        } else {
            Log.d("RsaCrypto", "Generating new keypair")
            val newKeyPair = generateKeyPair()
            saveKeyPair(newKeyPair, privateKeyFile, publicKeyFile)
            newKeyPair
        }
    }

    fun getPublicKeyBase64(): String {
        val encoded = keyPair.public.encoded
        return Base64.encodeToString(encoded, Base64.NO_WRAP)
    }

    fun rsaDecrypt(encryptedData: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keyPair.private)
        return cipher.doFinal(encryptedData)
    }

    fun aesDecrypt(key: ByteArray, encryptedData: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey)
        return cipher.doFinal(encryptedData)
    }

    fun aesEncrypt(key: ByteArray, data: ByteArray): ByteArray {
        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(AES_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher.doFinal(data)
    }


    private fun generateKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(KEY_SIZE)
        return keyGen.generateKeyPair()
    }

    private fun saveKeyPair(keyPair: KeyPair, privateKeyFile: File, publicKeyFile: File) {
        // Save private key
        val privateEncoded = keyPair.private.encoded
        val privateBase64 = Base64.encodeToString(privateEncoded, Base64.NO_WRAP)
        val privatePem = "-----BEGIN PRIVATE KEY-----\n${privateBase64.chunked(64).joinToString("\n")}\n-----END PRIVATE KEY-----"
        privateKeyFile.writeText(privatePem)
        Log.d("RsaCrypto", "Private key saved to ${privateKeyFile.absolutePath}")

        // Save public key
        val publicEncoded = keyPair.public.encoded
        val publicBase64 = Base64.encodeToString(publicEncoded, Base64.NO_WRAP)
        val publicPem = "-----BEGIN PUBLIC KEY-----\n${publicBase64.chunked(64).joinToString("\n")}\n-----END PUBLIC KEY-----"
        publicKeyFile.writeText(publicPem)
        Log.d("RsaCrypto", "Public key saved to ${publicKeyFile.absolutePath}")
    }

    private fun loadKeyPair(privateKeyFile: File, publicKeyFile: File): KeyPair {
        val privatePem = privateKeyFile.readText()
        val publicPem = publicKeyFile.readText()

        val privateKey = parsePrivateKeyFromPem(privatePem)
        val publicKey = parsePublicKeyFromPem(publicPem)

        return KeyPair(publicKey, privateKey)
    }

    private fun parsePrivateKeyFromPem(pem: String): PrivateKey {
        val privateKeyPEM = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")

        val encoded = Base64.decode(privateKeyPEM, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePrivate(PKCS8EncodedKeySpec(encoded))
    }

    private fun parsePublicKeyFromPem(pem: String): PublicKey {
        val publicKeyPEM = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replace("\\s".toRegex(), "")

        val encoded = Base64.decode(publicKeyPEM, Base64.DEFAULT)
        val keyFactory = KeyFactory.getInstance("RSA")
        return keyFactory.generatePublic(X509EncodedKeySpec(encoded))
    }
}
