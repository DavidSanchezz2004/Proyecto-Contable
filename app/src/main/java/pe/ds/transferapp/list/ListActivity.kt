package pe.ds.transferapp.view.list

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import cn.pedant.SweetAlert.SweetAlertDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import pe.ds.transferapp.databinding.ActivityListBinding
import pe.ds.transferapp.list.TransferAdapter
import pe.ds.transferapp.model.AppDatabase
import pe.ds.transferapp.model.Transferencia
import pe.ds.transferapp.view.editor.EditorActivity
import pe.ds.transferapp.view.export.ExportActivity

class ListActivity : AppCompatActivity() {

    private lateinit var binding: ActivityListBinding
    private lateinit var adapter: TransferAdapter

    private var allItems: List<Transferencia> = emptyList()
    private var query: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityListBinding.inflate(layoutInflater)
        setContentView(binding.root)

        supportActionBar?.title = "Transferencias – Lista"

        adapter = TransferAdapter(
            onClick = { item ->
                val i = Intent(this, EditorActivity::class.java)
                i.putExtra(EditorActivity.ARG_ID, item.id)
                startActivity(i)
            },
            onLongClick = { item ->
                SweetAlertDialog(this@ListActivity, SweetAlertDialog.SUCCESS_TYPE)
                    .setTitleText("Detalle rápido")
                    .setContentText(
                        "${item.banco} • ${item.fecha} ${item.hora}\n" +
                                "Beneficiario: ${item.beneficiario}\n" +
                                "Importe: ${item.importe}\n" +
                                "Nº Op: ${item.nro_operacion ?: "-"}"
                    )
                    .setConfirmText("OK")
                    .show()
                true
            }
        )


        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter

        setupSearch()

        binding.fabNew.setOnClickListener {
            startActivity(Intent(this, EditorActivity::class.java))
        }

        binding.btnExportar.setOnClickListener {
            startActivity(Intent(this, ExportActivity::class.java))
        }

        val dao = AppDatabase.getInstance(this).transferenciaDao()
        lifecycleScope.launch {
            dao.listAllFlow().collectLatest { list ->
                allItems = list
                applyFilterAndShow()
            }
        }
    }

    private fun setupSearch() {
        binding.searchView.isSubmitButtonEnabled = false
        binding.searchView.setIconifiedByDefault(false)
        binding.searchView.queryHint = "Buscar por beneficiario, importe o Nº operación"
        binding.searchView.clearFocus()

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(text: String?): Boolean {
                query = text?.trim().orEmpty()
                applyFilterAndShow()
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                query = newText?.trim().orEmpty()
                applyFilterAndShow()
                return true
            }
        })

        // botón X para limpiar
        val closeButtonId = binding.searchView.context.resources
            .getIdentifier("android:id/search_close_btn", null, null)
        binding.searchView.findViewById<View?>(closeButtonId)?.setOnClickListener {
            binding.searchView.setQuery("", false)
            binding.searchView.clearFocus()
            query = ""
            applyFilterAndShow()
        }
    }

    private fun applyFilterAndShow() {
        val q = query.lowercase(java.util.Locale.ROOT)

        val filtered = if (q.isBlank()) {
            allItems
        } else {
            allItems.filter { t ->
                val benef = t.beneficiario.lowercase(java.util.Locale.ROOT)
                val imp   = t.importe.lowercase(java.util.Locale.ROOT)
                val nro   = (t.nro_operacion ?: "").lowercase(java.util.Locale.ROOT)
                benef.contains(q) || imp.contains(q) || nro.contains(q)
            }
        }

        adapter.submitList(filtered)

        // 👇 usa .root porque emptyView es ViewEmptyBinding
        binding.emptyView.root.visibility =
            if (filtered.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE

        binding.tvHeader.text = if (q.isBlank())
            "Transferencias – ${filtered.size}"
        else
            "Transferencias – ${filtered.size} (buscando: \"$q\")"
    }

}
