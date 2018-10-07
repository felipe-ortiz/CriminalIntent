package com.bignerdranch.android.criminalintent

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.provider.MediaStore
import android.support.v4.app.Fragment
import android.support.v4.content.FileProvider
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import java.io.File

import java.util.*

class CrimeFragment: Fragment() {

    private val DIALOG_DATE = "DialogDate"
    private val REQUEST_DATE = 0
    private val REQUEST_CONTACT = 1
    private val REQUEST_PHOTO = 2

    lateinit var crime: Crime
    lateinit var titleField: EditText
    lateinit var dateButton: Button
    lateinit var solvedCheckedBox: CheckBox
    lateinit var reportButton: Button
    lateinit var suspectButton: Button
    lateinit var photoButton: ImageButton
    lateinit var photoView: ImageView
    lateinit var photoFile: File

    companion object {
        const val ARG_CRIME_ID = "crime_id"

        fun newInstance(crimeId: UUID) : CrimeFragment {
            var args = Bundle()
            args.putSerializable(ARG_CRIME_ID, crimeId)

            var fragment = CrimeFragment()
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val crimeId = arguments?.getSerializable(ARG_CRIME_ID) as UUID
        crime = CrimeLab.get(activity).getCrime(crimeId)
        photoFile = CrimeLab.get(activity).getPhotoFile(crime)
    }

    override fun onPause() {
        super.onPause()

        CrimeLab.get(activity).updateCrime(crime)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val v = inflater.inflate(R.layout.fragment_crime, container, false)

        titleField = v.findViewById(R.id.crime_title)
        titleField.setText(crime.title)
        titleField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                // This space intentionally left blank
            }

            override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                crime.title = p0.toString()
            }

            override fun afterTextChanged(p0: Editable?) {
                // This space intentionally left blank
            }
        })

        dateButton = v.findViewById(R.id.crime_date)
        updateDate()
        dateButton.setOnClickListener{
            val manager = fragmentManager
            val dialog = DatePickerFragment.newInstance(crime.date)
            dialog.setTargetFragment(this@CrimeFragment, REQUEST_DATE)
            dialog.show(manager, DIALOG_DATE)
        }

        solvedCheckedBox = v.findViewById(R.id.crime_solved)
        solvedCheckedBox.isChecked = crime.isSolved
        solvedCheckedBox.setOnCheckedChangeListener(object : CompoundButton.OnCheckedChangeListener {
            override fun onCheckedChanged(p0: CompoundButton?, p1: Boolean) {
                crime.isSolved = p1
            }
        })

        reportButton = v.findViewById(R.id.crime_report)
        reportButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                var i = Intent(Intent.ACTION_SEND)
                i.setType("text/plain")
                i.putExtra(Intent.EXTRA_TEXT, getCrimeReport())
                i.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.crime_report_subject))
                i = Intent.createChooser(i, getString(R.string.send_report))
                startActivity(i)
            }
        })

        val pickContact = Intent(Intent.ACTION_PICK, ContactsContract.Contacts.CONTENT_URI)
        suspectButton = v.findViewById(R.id.crime_suspect)
        suspectButton.setOnClickListener(object : View.OnClickListener{
            override fun onClick(p0: View?) {
                startActivityForResult(pickContact, REQUEST_CONTACT)
            }
        })

        if (crime.suspect != null) {
            suspectButton.setText(crime.suspect)
        }

        val packageManager = activity?.packageManager
        if (packageManager?.resolveActivity(pickContact, PackageManager.MATCH_DEFAULT_ONLY) == null) {
            suspectButton.isEnabled = false
        }

        photoButton = v.findViewById(R.id.crime_camera)

        val captureImage = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        val canTakePhoto = photoFile != null && captureImage.resolveActivity(packageManager) != null
        photoButton.isEnabled = canTakePhoto

        photoButton.setOnClickListener(object : View.OnClickListener {
            override fun onClick(p0: View?) {
                val uri = FileProvider.getUriForFile(activity as Context,
                        "com.bignerdranch.android.criminalintent.fileprovider",
                        photoFile)
                captureImage.putExtra(MediaStore.EXTRA_OUTPUT, uri)

                var cameraActivities = activity?.packageManager!!.queryIntentActivities(captureImage,
                        PackageManager.MATCH_DEFAULT_ONLY)
                for (a in cameraActivities) {
                    activity?.grantUriPermission(a.activityInfo.packageName,
                            uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                }

                startActivityForResult(captureImage, REQUEST_PHOTO)
            }
        })
        photoView = v.findViewById(R.id.crime_photo)
        updatePhotView()

        return v
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (resultCode != Activity.RESULT_OK) {
            return
        }

        if (requestCode == REQUEST_DATE) {
            val date = data?.getSerializableExtra(DatePickerFragment.EXTRA_DATE) as Date
            crime.date = date
            updateDate()
        } else if (requestCode == REQUEST_CONTACT && data != null) {
            val contactUri = data.getData()
            // Specify which fields you want your query to return values for
            val queryFields : Array<String> = {
                ContactsContract.Contacts.DISPLAY_NAME
            } as Array<String>
            // Perform your query - the contactUri is like a "where" clause here
            val c = activity?.contentResolver?.query(contactUri, queryFields, null, null, null)
            try {
                // Double-check that you actually got results
                if (c?.count == 0) {
                    return
                }

                // Pull out the first column of the first row of data - this is your suspect's name
                c?.moveToFirst()
                val suspect = c?.getString(0)
                crime.suspect = suspect
                suspectButton.text = suspect
            } finally {
                c?.close()
            }
        } else if (requestCode == REQUEST_PHOTO) {
            val uri = FileProvider.getUriForFile(activity as Context, "com.bignerdranch.android.criminalintent.fileprovider",
                    photoFile)
            getActivity()?.revokeUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

            updatePhotView()
        }

    }

    private fun updateDate() {
        dateButton.text = crime.date.toString()
    }

    private fun getCrimeReport() : String {
        var solvedString: String?
        if (crime.isSolved) {
            solvedString = getString(R.string.crime_report_solved)
        } else {
            solvedString = getString(R.string.crime_report_unsolved)
        }

        val dateFormat = "EEE, MM, dd"
        val dateString = DateFormat.format(dateFormat, crime.date).toString()

        var suspect = crime.suspect
        if (suspect == null) {
            suspect = getString(R.string.crime_report_no_suspect)
        } else {
            suspect = getString(R.string.crime_report_suspect, suspect)
        }

        return getString(R.string.crime_report, crime.title, dateString, solvedString, suspect)
    }

    private fun updatePhotView() {
        if (photoFile == null || !photoFile.exists()) {
            photoView.setImageDrawable(null)
        } else {
            val bitmap = PictureUtils.getScaledBitmap(photoFile.path, activity)
            photoView.setImageBitmap(bitmap)
        }
    }
}