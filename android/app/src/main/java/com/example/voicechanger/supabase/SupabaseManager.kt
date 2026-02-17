package com.example.voicechanger.supabase

import io.github.jan_tennert.supabase.createSupabaseClient
import io.github.jan_tennert.supabase.gotrue.GoTrue
import io.github.jan_tennert.supabase.postgrest.Postgrest
import io.github.jan_tennert.supabase.storage.Storage
import io.github.jan_tennert.supabase.storage.storage
import java.io.File
import java.util.UUID

class SupabaseManager(
    private val supabaseUrl: String = "https://YOUR_PROJECT_ID.supabase.co",
    private val supabaseKey: String = "YOUR_ANON_KEY"
) {
    private val client = createSupabaseClient(supabaseUrl, supabaseKey) {
        install(Storage)
        install(Postgrest)
        install(GoTrue)
    }

    suspend fun uploadAudio(file: File): String {
        val fileName = "${UUID.randomUUID()}.wav"
        val bucket = client.storage["recordings"]
        
        // Upload bytes
        bucket.upload(fileName, file.readBytes())
        
        // Get public URL (Assumes bucket is public)
        return bucket.publicUrl(fileName)
    }
}
