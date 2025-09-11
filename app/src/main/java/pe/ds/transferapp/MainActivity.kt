package pe.ds.transferapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import cn.pedant.SweetAlert.SweetAlertDialog
import pe.ds.transferapp.databinding.ActivityMainBinding
import pe.ds.transferapp.view.importer.ImportActivity
import pe.ds.transferapp.view.list.ListActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnHello.setOnClickListener {
            SweetAlertDialog(this, SweetAlertDialog.SUCCESS_TYPE)
                .setTitleText("¡Proyecto base listo!")
                .setContentText("Compiló y corrió con ViewBinding y SweetAlert.")
                .setConfirmText("OK")
                .show()
        }

        binding.btnOpenList.setOnClickListener {
            startActivity(Intent(this, ListActivity::class.java))
        }

        binding.btnOpenImport.setOnClickListener {
            startActivity(Intent(this, ImportActivity::class.java))
        }
    }
}
