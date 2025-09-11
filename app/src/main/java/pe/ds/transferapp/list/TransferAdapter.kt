package pe.ds.transferapp.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import pe.ds.transferapp.databinding.ItemTransferBinding
import pe.ds.transferapp.model.Transferencia

class TransferAdapter(
    private val onClick: (Transferencia) -> Unit,
    private val onLongClick: (Transferencia) -> Boolean
) : ListAdapter<Transferencia, TransferAdapter.VH>(Diff) {

    object Diff : DiffUtil.ItemCallback<Transferencia>() {
        override fun areItemsTheSame(oldItem: Transferencia, newItem: Transferencia): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Transferencia, newItem: Transferencia): Boolean = oldItem == newItem
    }

    class VH(val binding: ItemTransferBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemTransferBinding.inflate(inflater, parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        with(holder.binding) {
            tvLinea1.text = "${item.fecha} ${item.hora} • ${item.banco}"
            tvLinea2.text = "Op: ${item.nro_operacion ?: "-"} • Dest ****${item.cta_dest_ult4}"
            tvImporte.text = item.importe
            root.setOnClickListener { onClick(item) }
            root.setOnLongClickListener { onLongClick(item) }
        }
    }
}
