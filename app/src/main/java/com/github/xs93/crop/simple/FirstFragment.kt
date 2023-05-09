package com.github.xs93.crop.simple

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.github.xs93.crop.simple.databinding.FragmentFirstBinding

/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    private var _binding: FragmentFirstBinding? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {

        _binding = FragmentFirstBinding.inflate(inflater, container, false)

        return binding.root

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCrop.setOnClickListener {
            binding.cropView.getCropBitmap(512)
        }
        binding.btn1.setOnClickListener {
            binding.cropView.setCropRatio(1f, 1f)
        }
        binding.btn2.setOnClickListener {
            binding.cropView.setCropRatio(2f, 3f)
        }

        binding.btn3.setOnClickListener {
            binding.cropView.setCropRatio(3f, 2f)
        }

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}