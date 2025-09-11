package pe.ds.transferapp.view.list

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pe.ds.transferapp.databinding.ActivityListBinding
import pe.ds.transferapp.list.TransferAdapter
import pe.ds.transferapp.model.AppDatabase
import pe.ds.transferapp.view.Confirm
import pe.ds.transferapp.view.editor.EditorActivity

class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: TransferAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adapter = TransferAdapter(
            onClick = { item ->
                // Abrir editor en modo edición
                startActivity(Intent(this, EditorActivity::class.java).apply {
                    putExtra(EditorActivity.ARG_ID, item.id)
                })
            },
            onLongClick = { item ->
                // Solo demostración de confirmación (sin eliminar aquí)
                lifecycleScope.launch {
                    val ok = Confirm.confirmDelete(this@ListActivity)
                    if (ok) {
                        Confirm.showSuccess(this@ListActivity, "OK", "Aquí no eliminamos; usa el Editor.")
                    } else {
                        Confirm.showError(this@ListActivity, "Cancelado", "No se eliminó.")
                    }
                }
                true
            }
        )

        binding.recycler.apply {
            layoutManager = LinearLayoutManager(this@ListActivity)
            adapter = this@ListActivity.adapter
        }

        val dao = AppDatabase.getInstance(this).transferenciaDao()
        lifecycleScope.launch {
            dao.listAllFlow().collectLatest { list ->
                adapter.submitList(list)
                binding.emptyView.visibility = if (list.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        binding.btnBack.setOnClickListener { finish() }

        // FAB: crear nuevo
        binding.fabNew.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }
    }
}
