package com.example.anki;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CardAdapter extends RecyclerView.Adapter<CardAdapter.CardViewHolder> {

    private List<Card> cardList = new ArrayList<>();
    private OnStarClickListener starClickListener;

    //カードの保存用リスト
    private boolean isDeleteMode = false;
    private Set<Card> selectedCards = new HashSet<>();

    public interface OnStarClickListener {
        void onStarClick(Card card);
    }

    public void setOnStarClickListener(OnStarClickListener listener) {
        this.starClickListener = listener;
    }

    public void setCards(List<Card> cards) {
        this.cardList = cards;
        notifyDataSetChanged();
    }

    // ★追加：削除モードの切り替え用メソッド
    public void toggleDeleteMode(boolean enabled) {
        this.isDeleteMode = enabled;
        this.selectedCards.clear(); // 切り替え時は選択状態をリセット
        notifyDataSetChanged(); // 画面全体を再描画
    }

    // ★追加：選択されたカードのリストを取得するメソッド
    public List<Card> getSelectedCards() {
        return new ArrayList<>(selectedCards);
    }

    @NonNull
    @Override
    public CardViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_card, parent, false);
        return new CardViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CardViewHolder holder, int position) {
        Card currentCard = cardList.get(position);

        String tag = currentCard.category != null ? currentCard.category : "タグなし";
        holder.textContent.setText(currentCard.question + " / " + currentCard.answer + " / " + tag);

        if (currentCard.lastResult == 1) {
            holder.imageStar.setColorFilter(Color.RED);
        } else if (currentCard.lastResult == 2) {
            holder.imageStar.setColorFilter(Color.BLUE);
        } else {
            holder.imageStar.setColorFilter(Color.DKGRAY);
        }

        // ★追加：削除モード時のUI制御
        if (isDeleteMode) {
            holder.checkboxDelete.setVisibility(View.VISIBLE);
            holder.imageStar.setVisibility(View.GONE); // 誤操作防止のため星マークを隠す

            // 再利用時のリスナー誤動作を防ぐため、一度リスナーを解除する
            holder.checkboxDelete.setOnCheckedChangeListener(null);
            holder.checkboxDelete.setChecked(selectedCards.contains(currentCard));

            // チェックボックスを直接タップした時の処理
            holder.checkboxDelete.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (isChecked) {
                    selectedCards.add(currentCard);
                } else {
                    selectedCards.remove(currentCard);
                }
            });

            // 行全体をタップしてもチェックが入るようにする
            holder.itemView.setOnClickListener(v -> {
                holder.checkboxDelete.setChecked(!holder.checkboxDelete.isChecked());
            });

        } else {
            // 通常時のUI制御
            holder.checkboxDelete.setVisibility(View.GONE);
            holder.imageStar.setVisibility(View.VISIBLE);
            holder.itemView.setOnClickListener(null); // 通常時は行全体のクリックを無効化
        }

        holder.imageStar.setOnClickListener(v -> {
            if (starClickListener != null && !isDeleteMode) {
                starClickListener.onStarClick(currentCard);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cardList.size();
    }

    static class CardViewHolder extends RecyclerView.ViewHolder {
        TextView textContent;
        ImageView imageStar;
        CheckBox checkboxDelete;

        public CardViewHolder(@NonNull View itemView) {
            super(itemView);
            textContent = itemView.findViewById(R.id.text_card_content);
            imageStar = itemView.findViewById(R.id.image_star);
            checkboxDelete = itemView.findViewById(R.id.checkboxDelete);
        }
    }
}