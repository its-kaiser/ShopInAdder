package com.example.productadder

import android.content.Intent
import android.content.Intent.ACTION_GET_CONTENT
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.productadder.databinding.ActivityMainBinding
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.storage.ktx.storage
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private val binding by lazy {ActivityMainBinding.inflate(layoutInflater)}

    private var selectedImages = mutableListOf<Uri>()

    private val selectedColors = mutableListOf<Int>()

    private val productStorage = Firebase.storage.reference
    private val fbStore = Firebase.firestore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        binding.btnColorPicker.setOnClickListener{
            ColorPickerDialog.Builder(this)
                .setTitle("Product color")
                .setPositiveButton("Select", object : ColorEnvelopeListener{
                    override fun onColorSelected(envelope: ColorEnvelope?, fromUser: Boolean) {
                        envelope?.let {
                            selectedColors.add(it.color)
                            updateColors()
                        }
                    }
                })
                .setNegativeButton("Cancel"){colorPicker, _->
                    colorPicker.dismiss()
                }.show()
        }

        val selectImagesActivityResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){ result->
            if(result.resultCode== RESULT_OK){
                val retrievedImages = result.data

                //when multiple images has been selected
                if(retrievedImages?.clipData!=null){
                    val count = retrievedImages.clipData?.itemCount?:0

                    (0 until count).forEach{
                        val imageUri = retrievedImages.clipData?.getItemAt(it)?.uri

                        imageUri?.let {
                            selectedImages.add(it)
                        }
                    }
                }else{
                    val imageUri = retrievedImages?.data
                    imageUri?.let {
                        selectedImages.add(it)
                    }
                }
                updateImages()
            }

        }
        binding.btnImagesPicker.setOnClickListener{
            val intent = Intent(ACTION_GET_CONTENT)
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            intent.type = "image/*"
            selectImagesActivityResult.launch(intent)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.toolbar_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if(item.itemId==R.id.saveProduct){
            val productValidation = validateInformation()

            if(!productValidation){
                Toast.makeText(this, "Check your inputs",Toast.LENGTH_SHORT).show()
                return false
            }

            saveProducts()
        }
        return super.onOptionsItemSelected(item)
    }

    private fun updateImages() {
        binding.tvSelectedImages.text = selectedImages.size.toString()
    }

    private fun updateColors() {
        var colors = ""
        selectedColors.forEach{
            colors ="$colors ${Integer.toHexString(it)}"
        }
        binding.tvSelectedColors.text = colors
    }
    private fun saveProducts() {
        val name = binding.etName.text.toString().trim()
        val category = binding.etCategory.text.toString().trim()
        val price = binding.etPrice.text.toString().trim()
        val offerPercentage = binding.etOfferPercentage.text.toString().trim()
        val description = binding.etDescription.text.toString().trim()
        val sizes = getSizesList(binding.etSizes.text.toString().trim())
        val imagesByteArrays = getImagesByteArrays()
        val images = mutableListOf<String>()

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main){
                showLoading()
            }
            try {
                async {
                    imagesByteArrays.forEach {
                        val id = UUID.randomUUID().toString()
                        launch {
                            val imageStorage = productStorage.child("products/images/$id")
                            val result = imageStorage.putBytes(it).await()

                            val downloadUrl = result.storage.downloadUrl.await().toString()
                            images.add(downloadUrl)
                        }
                    }
                }.await()
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main){
                    hideLoading()
                }
            }

            val product = Product(
                UUID.randomUUID().toString(),
                name,
                category,
                price.toFloat(),
                if (offerPercentage.isEmpty()) null else offerPercentage.toFloat(),
                if (description.isEmpty()) null else description,
                if (selectedColors.isEmpty()) null else selectedColors,
                sizes,
                images
            )
            fbStore.collection("Products").add(product).addOnSuccessListener {
                hideLoading()
            }.addOnFailureListener{
                hideLoading()
                Log.e("Error",it.message.toString())
            }
        }
    }

    private fun hideLoading() {
        binding.progressBar.visibility = View.INVISIBLE
    }

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
    }

    private fun getImagesByteArrays(): List<ByteArray> {
        val imagesByteArray =  mutableListOf<ByteArray>()
        selectedImages.forEach{
            val stream = ByteArrayOutputStream()
            val imageBmp = MediaStore.Images.Media.getBitmap(contentResolver, it)
            if(imageBmp.compress(Bitmap.CompressFormat.JPEG, 100 , stream)){
                imagesByteArray.add(stream.toByteArray())
            }
        }
        return imagesByteArray
    }

    private fun getSizesList(sizesStr: String): List<String>? {
        if (sizesStr.isEmpty()) {
            return null
        }
        return sizesStr.split(",")
    }

    private fun validateInformation(): Boolean {
        if(binding.etPrice.text.toString().trim().isEmpty()){
            return false
        }
        if(binding.etName.text.toString().trim().isEmpty()){
            return false
        }
        if(binding.etCategory.text.toString().trim().isEmpty()){
            return false
        }
        if(selectedImages.isEmpty()){
            return false
        }
        return true
    }
}