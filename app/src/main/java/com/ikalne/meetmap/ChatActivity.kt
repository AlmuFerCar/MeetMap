package com.ikalne.meetmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import com.ikalne.meetmap.adapters.MessageAdapter
import com.ikalne.meetmap.models.Message
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.ikalne.meetmap.databinding.ActivityChatBinding


class ChatActivity : AppCompatActivity() {
    lateinit var binding: ActivityChatBinding
    private var toolbar: Toolbar? = null
    private var chatId = ""
    private var user = ""
    private var name = ""


    private var db = Firebase.firestore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)
        binding = ActivityChatBinding.inflate(layoutInflater)
        setContentView(binding.root)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)

        supportActionBar?.setDisplayHomeAsUpEnabled(true)


        intent.getStringExtra("chatId")?.let { chatId = it }
        intent.getStringExtra("user")?.let { user = it }

        name = intent.getStringExtra("name").toString()

        // Ahora puedes usar la variable name como una cadena en tu actividad
        Log.d("TAG", "El nombre es $name")




        if(chatId.isNotEmpty() && user.isNotEmpty()) {
            initViews()
        }
    }

    private fun initViews(){
        binding.messagesRecylerView.layoutManager = LinearLayoutManager(this)
        binding.messagesRecylerView.adapter = MessageAdapter(user)
        val text= name.substringBefore("@")
        binding.toolbar.title = "Chat con $text"
        binding.sendMessageButton.setOnClickListener { sendMessage() }


        val chatRef = db.collection("chats").document(chatId)

        chatRef.collection("messages").orderBy("dob", Query.Direction.ASCENDING)
            .get()
            .addOnSuccessListener { messages ->
                val listMessages = messages.toObjects(Message::class.java)
                (binding.messagesRecylerView.adapter as MessageAdapter).setData(listMessages)
            }

        chatRef.collection("messages").orderBy("dob", Query.Direction.ASCENDING)
            .addSnapshotListener { messages, error ->
                if(error == null){
                    messages?.let {
                        val listMessages = it.toObjects(Message::class.java)
                        (binding.messagesRecylerView.adapter as MessageAdapter).setData(listMessages)
                    }
                }
            }
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun sendMessage(){
        val message = Message(
            message = binding.messageTextField.text.toString(),
            from = user
        )

        db.collection("chats").document(chatId).collection("messages").document().set(message)

        binding.messageTextField.setText("")


    }
}