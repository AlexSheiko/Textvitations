package sheyko.aleksey.eventy;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

class TemplateGridAdapter extends BaseAdapter {
    private List<Item> items = new ArrayList<Item>();
    private LayoutInflater inflater;

    public TemplateGridAdapter(Context context, String mCategory) {
        inflater = LayoutInflater.from(context);

            if (mCategory.contains("Party")) {
                items.add(new Item("Sunflower", R.drawable.party_template_1));
                items.add(new Item("Sunrise", R.drawable.party_template_2));
                items.add(new Item("Balloons", R.drawable.party_template_3));
                items.add(new Item("Heart", R.drawable.party_template_4));
                items.add(new Item("Music", R.drawable.party_template_5));
                items.add(new Item("Masquerade (blue)", R.drawable.party_template_6));
                items.add(new Item("Masquerade (pink)", R.drawable.party_template_7));
                items.add(new Item("Sparks", R.drawable.party_template_8));
                items.add(new Item("Pink vignettes", R.drawable.party_template_9));
                items.add(new Item("Grey vignettes", R.drawable.party_template_10));
                items.add(new Item("Light blue", R.drawable.party_template_11));
                items.add(new Item("Dark blue", R.drawable.party_template_12));
                items.add(new Item("Party", R.drawable.party_template_13));
                items.add(new Item("Frame", R.drawable.party_template_14));

            } else if (mCategory.contains("Birthday")) {
                items.add(new Item("Сhampagne", R.drawable.birthday_template_1));
                items.add(new Item("Blue pattern", R.drawable.birthday_template_2));
                items.add(new Item("Orange box", R.drawable.birthday_template_3));
                items.add(new Item("Red tape", R.drawable.birthday_template_4));
                items.add(new Item("Balloons", R.drawable.birthday_template_5));
                items.add(new Item("Birthday gift", R.drawable.birthday_template_6));
                items.add(new Item("Green leaf", R.drawable.birthday_template_7));
                items.add(new Item("Happy Birthday", R.drawable.birthday_template_8));
                items.add(new Item("Housewife", R.drawable.birthday_template_9));
                items.add(new Item("Hearts", R.drawable.birthday_template_10));
                items.add(new Item("Orange ribbons", R.drawable.birthday_template_11));

            } else if (mCategory.contains("Holidays")) {
                items.add(new Item("Snow forest", R.drawable.holidays_template_1));
                items.add(new Item("Christmas tree", R.drawable.holidays_template_2));
                items.add(new Item("Balls & sparks", R.drawable.holidays_template_3));
                items.add(new Item("Santa", R.drawable.holidays_template_4));
                items.add(new Item("Winter", R.drawable.holidays_template_5));
                items.add(new Item("Snowflake", R.drawable.holidays_template_6));
                items.add(new Item("Christmas balls", R.drawable.holidays_template_7));
                items.add(new Item("Bell", R.drawable.holidays_template_8));
                items.add(new Item("Snowman", R.drawable.holidays_template_9));
                items.add(new Item("Blue balls", R.drawable.holidays_template_10));
                items.add(new Item("Thanksgiving Piece", R.drawable.holidays_template_11));
                items.add(new Item("Magician's hat", R.drawable.holidays_template_12));
                items.add(new Item("Rabbit", R.drawable.holidays_template_13));
                items.add(new Item("Autumn leaves", R.drawable.holidays_template_14));
                items.add(new Item("Hearts", R.drawable.holidays_template_15));
                items.add(new Item("Valentines day", R.drawable.holidays_template_16));
                items.add(new Item("Trailing leaves", R.drawable.holidays_template_17));
                items.add(new Item("Eastern eggs", R.drawable.holidays_template_18));
                items.add(new Item("Eggs pattern", R.drawable.holidays_template_19));
                items.add(new Item("4th of July", R.drawable.holidays_template_20));

            } else if (mCategory.contains("Kids")) {
                items.add(new Item("Teddy bear", R.drawable.kids_template_1));
                items.add(new Item("Baseball", R.drawable.kids_template_2));
                items.add(new Item("Confetti", R.drawable.kids_template_3));
                items.add(new Item("Cake", R.drawable.kids_template_4));
                items.add(new Item("Balloons", R.drawable.kids_template_5));
                items.add(new Item("Balloons 2", R.drawable.kids_template_6));
                items.add(new Item("Gifts", R.drawable.kids_template_7));
                items.add(new Item("Postcard", R.drawable.kids_template_8));
                items.add(new Item("Baby", R.drawable.kids_template_9));
                items.add(new Item("1 year", R.drawable.kids_template_10));
                items.add(new Item("Clouds", R.drawable.kids_template_11));
                items.add(new Item("Clouds 2", R.drawable.kids_template_12));
                items.add(new Item("Gifts", R.drawable.kids_template_13));
                items.add(new Item("Birthday", R.drawable.kids_template_14));

            } else if (mCategory.contains("Baby Shower")) {
                items.add(new Item("Lovely baby", R.drawable.baby_shower_template_1));
                items.add(new Item("Blue buggy", R.drawable.baby_shower_template_2));
                items.add(new Item("Pink buggy", R.drawable.baby_shower_template_3));
                items.add(new Item("Baby toys", R.drawable.baby_shower_template_4));
                items.add(new Item("Baby's bib", R.drawable.baby_shower_template_5));
                items.add(new Item("Lamb", R.drawable.baby_shower_template_6));
                items.add(new Item("Baby arrival", R.drawable.baby_shower_template_7));
                items.add(new Item("Drying clothes", R.drawable.baby_shower_template_8));
                items.add(new Item("Night sleep", R.drawable.baby_shower_template_9));
                items.add(new Item("Squares", R.drawable.baby_shower_template_10));

            } else if (mCategory.contains("Wedding Anniversary")) {
                items.add(new Item("Rings", R.drawable.wedding_anniversary_template_1));
                items.add(new Item("Champagne", R.drawable.wedding_anniversary_template_2));
                items.add(new Item("Golden vignette", R.drawable.wedding_anniversary_template_3));
                items.add(new Item("Wineglass", R.drawable.wedding_anniversary_template_4));
                items.add(new Item("Hearts", R.drawable.wedding_anniversary_template_5));
                items.add(new Item("Wineglass", R.drawable.wedding_anniversary_template_6));
                items.add(new Item("Rose petals", R.drawable.wedding_anniversary_template_7));
                items.add(new Item("Wineglass", R.drawable.wedding_anniversary_template_8));
                items.add(new Item("Champagne", R.drawable.wedding_anniversary_template_9));
                items.add(new Item("Rings", R.drawable.wedding_anniversary_template_10));
                items.add(new Item("Table for Two", R.drawable.wedding_anniversary_template_11));
                items.add(new Item("Rose & rings", R.drawable.wedding_anniversary_template_12));
                items.add(new Item("Stemware", R.drawable.wedding_anniversary_template_13));
                items.add(new Item("Anniversary", R.drawable.wedding_anniversary_template_14));
                items.add(new Item("50 years", R.drawable.wedding_anniversary_template_15));
            }


    }

    @Override
    public int getCount() {
        return items.size();
    }

    @Override
    public Object getItem(int i) {
        return items.get(i);
    }

    @Override
    public long getItemId(int i) {
        return items.get(i).drawableId;
    }

    @Override
    public View getView(int i, View view, ViewGroup viewGroup) {
        View v = view;
        ImageView picture;
        TextView name;

        if (v == null) {
            v = inflater.inflate(R.layout.template_grid_item, viewGroup, false);
            v.setTag(R.id.picture, v.findViewById(R.id.picture));
            v.setTag(R.id.text, v.findViewById(R.id.text));
        }

        picture = (ImageView) v.getTag(R.id.picture);
        name = (TextView) v.getTag(R.id.text);

        Item item = (Item) getItem(i);

        picture.setImageResource(item.drawableId);
        name.setText(item.name);

        return v;
    }

    private class Item {
        final String name;
        final int drawableId;

        Item(String name, int drawableId) {
            this.name = name;
            this.drawableId = drawableId;
        }
    }
}
