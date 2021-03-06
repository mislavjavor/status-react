(ns status-im.contacts.subs
  (:require-macros [reagent.ratom :refer [reaction]])
  (:require [re-frame.core :refer [register-sub subscribe]]
            [status-im.utils.identicon :refer [identicon]]))

(register-sub :current-contact
  (fn [db [_ k]]
    (-> @db
        (get-in [:contacts (:current-chat-id @db) k])
        (reaction))))

(register-sub :get-contacts
  (fn [db _]
    (let [contacts (reaction (:contacts @db))]
      (reaction @contacts))))

(defn sort-contacts [contacts]
  (sort (fn [c1 c2]
          (let [name1 (or (:name c1) (:address c1) (:whisper-identity c1))
                name2 (or (:name c2) (:address c2) (:whisper-identity c2))]
            (compare (clojure.string/lower-case name1)
                     (clojure.string/lower-case name2))))
        (vals contacts)))

(register-sub :all-added-contacts
  (fn [db _]
    (let [contacts (reaction (:contacts @db))]
      (->> (remove #(true? (:pending? (second %))) @contacts)
           (sort-contacts)
           (reaction)))))

(register-sub :all-added-people
  (fn []
    (let [contacts (subscribe [:all-added-contacts])]
      (reaction (remove :dapp? @contacts)))))

(register-sub :all-added-dapps
  (fn []
    (let [contacts (subscribe [:all-added-contacts])]
      (reaction (filter :dapp? @contacts)))))

(register-sub :get-added-people-with-limit
  (fn [_ [_ limit]]
    (let [contacts (subscribe [:all-added-people])]
      (reaction (take limit @contacts)))))

(register-sub :get-added-dapps-with-limit
  (fn [_ [_ limit]]
    (let [contacts (subscribe [:all-added-dapps])]
      (reaction (take limit @contacts)))))

(register-sub :added-people-count
  (fn [_ _]
    (let [contacts (subscribe [:all-added-people])]
      (reaction (count @contacts)))))

(register-sub :added-dapps-count
  (fn [_ _]
    (let [contacts (subscribe [:all-added-dapps])]
      (reaction (count @contacts)))))

(defn get-contact-letter [contact]
  (when-let [letter (first (:name contact))]
    (clojure.string/upper-case letter)))

(register-sub :contacts-with-letters
  (fn [db _]
    (let [contacts (reaction (:contacts @db))
          pred     (subscribe [:get :contacts-filter])]
      (reaction
        (let [ordered (sort-contacts @contacts)
              ordered (if @pred (filter @pred ordered) ordered)]
          (reduce (fn [prev cur]
                    (let [prev-letter (get-contact-letter (last prev))
                          cur-letter  (get-contact-letter cur)]
                      (conj prev
                            (if (not= prev-letter cur-letter)
                              (assoc cur :letter cur-letter)
                              cur))))
                  [] ordered))))))

(defn contacts-by-chat [fn db chat-id]
  (let [chat     (reaction (get-in @db [:chats chat-id]))
        contacts (reaction (:contacts @db))]
    (reaction
      (when @chat
        (let [current-participants (->> @chat
                                        :contacts
                                        (map :identity)
                                        set)]
          (fn #(current-participants (:whisper-identity %))
              (vals @contacts)))))))

(defn contacts-by-current-chat [fn db]
  (let [current-chat-id (:current-chat-id @db)]
    (contacts-by-chat fn db current-chat-id)))

(register-sub :contact
  (fn [db _]
    (let [identity (:contact-identity @db)]
      (reaction (get-in @db [:contacts identity])))))

(register-sub :contact-by-identity
  (fn [db [_ identity]]
    (reaction (get-in @db [:contacts identity]))))

(register-sub :all-new-contacts
  (fn [db _]
    (contacts-by-current-chat remove db)))

(register-sub :current-chat-contacts
  (fn [db _]
    (contacts-by-current-chat filter db)))

(register-sub :chat-photo
  (fn [db [_ chat-id]]
    (let [chat-id  (or chat-id (:current-chat-id @db))
          chat     (reaction (get-in @db [:chats chat-id]))
          contacts (contacts-by-chat filter db chat-id)]
      (reaction
        (when (and @chat (not (:group-chat @chat)))
          (cond
            (:photo-path @chat)
            (:photo-path @chat)

            (pos? (count @contacts))
            (:photo-path (first @contacts))

            :else
            (identicon chat-id)))))))
