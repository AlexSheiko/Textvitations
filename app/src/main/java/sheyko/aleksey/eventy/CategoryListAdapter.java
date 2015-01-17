package sheyko.aleksey.eventy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class CategoryListAdapter extends ArrayAdapter<String> {
    private final Context context;
    private final String[] values;

    public CategoryListAdapter(Context context, String[] values) {
        super(context, R.layout.category_list_item, values);
        this.context = context;
        this.values = values;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        View rowView = inflater.inflate(R.layout.category_list_item, parent, false);
        TextView textView = (TextView) rowView.findViewById(R.id.category_title);
        ImageView imageView = (ImageView) rowView.findViewById(R.id.category_icon);
        textView.setText(values[position]);

        if (position == 0) {
            imageView.setImageResource(R.drawable.ic_category_1);
        } else if (position == 1) {
            imageView.setImageResource(R.drawable.ic_category_2);
        } else if (position == 2) {
            imageView.setImageResource(R.drawable.ic_category_3);
        } else if (position == 3) {
            imageView.setImageResource(R.drawable.ic_category_4);
        } else if (position == 4) {
            imageView.setImageResource(R.drawable.ic_category_5);
        } else if (position == 5) {
            imageView.setImageResource(R.drawable.ic_category_6);
        }

        return rowView;
    }
}
