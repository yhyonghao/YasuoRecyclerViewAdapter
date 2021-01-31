package com.fusion_nex_gen.yasuorvadapter.holder

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.fusion_nex_gen.yasuorvadapter.YasuoRVViewBindingAdapter

class YasuoViewBindingVH(root: View) :
    RecyclerView.ViewHolder(root) {
      lateinit var binding: ViewBinding

      fun isInitBinding() = ::binding.isInitialized

    /**
     * 由于ViewBinding没有像DataBinding一样的DataBindingUtil来根据泛型创建实体的方法
     * 因此如果使用ViewBinding，那么必须在[YasuoRVViewBindingAdapter.holderBind]中使用对应的ViewBinding.bind初始化binding
     * 例如ItemLayoutImageExBinding.bind(it)
     */
    fun <VB : ViewBinding> createBinding(createBinding: (view: View) -> VB): VB {
        if (!isInitBinding()) {
            binding = createBinding(itemView)
        }
        return binding as VB
    }
}