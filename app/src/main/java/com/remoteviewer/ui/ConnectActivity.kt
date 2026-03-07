package com.remoteviewer.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.remoteviewer.databinding.ActivityConnectBinding

class ConnectActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConnectBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityConnectBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnConnect.setOnClickListener {
            val roomId = binding.etRoomId.text.toString().trim().uppercase()
            if (roomId.length < 6) {
                binding.tilRoomId.error = "Enter a valid Room ID"
                return@setOnClickListener
            }
            binding.tilRoomId.error = null
            val intent = Intent(this, ViewerActivity::class.java)
                .putExtra("ROOM_ID", roomId)
            startActivity(intent)
        }
    }
}
