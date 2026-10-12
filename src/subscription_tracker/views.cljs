;; [[file:../../README.org::*NS: subscription-tracker.views][NS: subscription-tracker.views:1]]
(ns subscription-tracker.views
  (:require
   [clojure.string :as str]
   [reagent.core :as r]
   [re-frame.core :as re-frame]
   [subscription-tracker.events :as events]
   [subscription-tracker.subs :as subs]
   [subscription-tracker.util :as util]))

;; subscription-card/account-card (below) render subscription-form/
;; account-form (defined further down) when editing --- ClojureScript
;; compiles top-to-bottom, so these forward declarations are needed for
;; those references to resolve.
(declare subscription-form)
(declare account-form)

(def ^:private month-names
  ["January" "February" "March" "April" "May" "June"
   "July" "August" "September" "October" "November" "December"])

(defn- frequency-shorthand
  "Compact unit for the combined cost/frequency display, e.g. \"mo\" in
   \"$9.99/mo\"."
  [{:keys [frequency custom-interval-days]}]
  (case frequency
    :weekly "wk"
    :monthly "mo"
    :quarterly "3mo"
    :yearly "yr"
    :custom (str custom-interval-days "d")))

(defn- format-cost-frequency
  "\"$cost/shorthand\", plus a parenthetical normalized monthly estimate
   for anything that doesn't already bill monthly --- see the note above
   this component for why."
  [{:keys [cost frequency] :as subscription}]
  (let [base (str "$" (.toFixed cost 2) "/" (frequency-shorthand subscription))]
    (if (= frequency :monthly)
      base
      (str base " (~$" (.toFixed (util/monthly-cost subscription) 2) "/mo)"))))

(defn- status-symbol
  "Compact glyph for :status, with a trailing \"?\" when an :active
   subscription is past its own :end-date --- see the note above this
   component."
  [{:keys [status] :as subscription}]
  (case status
    :active (if (util/past-end-date? subscription) "↻?" "↻")
    :paused "⏸"
    :cancelled "⊘"))

(defn- status-title
  "Hover text for `status-symbol`, standing in for a header/legend row."
  [{:keys [status] :as subscription}]
  (case status
    :active (if (util/past-end-date? subscription)
              "Active, but past its end date --- check this"
              "Active")
    :paused "Paused"
    :cancelled "Cancelled"))

(defn subscription-details
  "Full field detail shown when a card is expanded."
  [{:keys [frequency billing-day billing-month billing-weekday start-date
           end-date category auto-renew? url account-id notes]}]
  (let [account @(re-frame/subscribe [::subs/account account-id])
        category-color @(re-frame/subscribe [::subs/category-color category])]
    [:div.subscription-details
     (cond
       (and (= frequency :yearly) billing-month billing-day)
       [:div [:span.field-label "Billing date: "]
        (str (nth month-names (dec billing-month)) " " billing-day)]

       billing-day [:div [:span.field-label "Billing day: "] billing-day]
       billing-weekday [:div [:span.field-label "Bills on: "] (str/capitalize (name billing-weekday))])
     (when start-date
       [:div [:span.field-label "Started: "] start-date])
     (when end-date
       [:div [:span.field-label "Ends: "] end-date])
     (when category
       [:div [:span.field-label "Category: "]
        (when category-color
          [:span.category-swatch {:style {:background-color category-color}}])
        [:span.category-value category]])
     [:div [:span.field-label "Will Auto-Renew: "] (if auto-renew? "Yes" "No")]
     (when account
       [:div [:span.field-label "Account: "]
        (str (:name account) " (••" (:last-digits account) ")")])
     (when url
       [:div [:span.field-label "Manage: "]
        [:a {:href url :target "_blank" :rel "noopener noreferrer"} url]])
     (when (seq notes)
       [:div.subscription-notes notes])]))

(defn subscription-card
  "A single subscription; click the summary row to expand/collapse full
   detail, and toggle editing/deletion within the expanded view."
  [_]
  (let [expanded? (r/atom false)
        editing? (r/atom false)
        confirming? (r/atom false)]
    (fn [{sub-name :name :keys [id status category] :as subscription}]
      (let [category-color @(re-frame/subscribe [::subs/category-color category])
            inactive? (or (= status :cancelled) (util/past-end-date? subscription))]
        [:div.subscription-card
         {:class (when inactive? "subscription-card--cancelled")
          :style {:border-left (when category-color (str "4px solid " category-color))}}
         [:div.subscription-summary {:on-click #(when-not @editing? (swap! expanded? not))
                                      :style {:cursor (if @editing? "default" "pointer")}}
          [:span.subscription-name sub-name]
          [:span.subscription-cost-freq (format-cost-frequency subscription)]
          [:span.subscription-status {:title (status-title subscription)}
           (status-symbol subscription)]]
         (when @expanded?
           (cond
             @editing?
             [subscription-form {:initial subscription
                                  :on-success #(reset! editing? false)
                                  :on-cancel #(reset! editing? false)}]

             @confirming?
             [:div.confirm-prompt
              [:span "Delete this subscription?"]
              [:button.danger {:type "button"
                                :on-click #(re-frame/dispatch [::events/remove-subscription id])}
               "Delete"]
              [:button {:type "button" :on-click #(reset! confirming? false)} "Cancel"]]

             :else
             [:div
              [subscription-details subscription]
              [:div.card-actions
               [:button.edit-toggle {:type "button"
                                      :on-click #(reset! editing? true)}
                "Edit"]
               [:button.delete-toggle {:type "button"
                                        :on-click #(reset! confirming? true)}
                "Delete"]]]))]))))

(defn subscription-list []
  (let [all @(re-frame/subscribe [::subs/subscription-list])
        subscriptions @(re-frame/subscribe [::subs/filtered-subscriptions])]
    [:div.subscription-list
     (cond
       (empty? all) [:p.empty-state "No subscriptions yet."]
       (empty? subscriptions) [:p.empty-state "No subscriptions match the current filters."]
       :else (doall
              (for [{:keys [id] :as subscription} subscriptions]
                ^{:key id} [subscription-card subscription])))]))

(def ^:private empty-subscription-form
  {:name "" :cost "" :frequency :monthly :custom-interval-days ""
   :billing-day "" :billing-month "" :billing-weekday "" :start-date "" :end-date ""
   :category "" :account-id ""
   :auto-renew? true :url "" :notes ""})

(defn- blank->nil [s]
  (when (seq s) s))

(defn- form->subscription
  "Coerces raw string form state into the shape ::events/add-subscription expects."
  [{:keys [name cost frequency custom-interval-days billing-day billing-month
           billing-weekday start-date end-date category account-id auto-renew? url notes]}]
  {:name name
   :cost (js/parseFloat cost)
   :frequency frequency
   :custom-interval-days (when (= frequency :custom) (js/parseInt custom-interval-days))
   :billing-day (when (contains? #{:monthly :quarterly :yearly} frequency)
                  (js/parseInt billing-day))
   :billing-month (when (= frequency :yearly) (js/parseInt billing-month))
   :billing-weekday (when (and (= frequency :weekly) (seq billing-weekday))
                       (keyword billing-weekday))
   :start-date (blank->nil start-date)
   :end-date (blank->nil end-date)
   :category (blank->nil category)
   :account-id (blank->nil account-id)
   :auto-renew? auto-renew?
   :url (blank->nil url)
   :notes (blank->nil notes)})

(defn- subscription->form
  "Inverse of form->subscription: seeds form state from an existing subscription."
  [{sub-name :name :keys [cost frequency custom-interval-days billing-day billing-month
                           billing-weekday start-date end-date category account-id auto-renew? url notes]}]
  {:name sub-name
   :cost (str cost)
   :frequency frequency
   :custom-interval-days (if custom-interval-days (str custom-interval-days) "")
   :billing-day (if billing-day (str billing-day) "")
   :billing-month (if billing-month (str billing-month) "")
   :billing-weekday (if billing-weekday (name billing-weekday) "")
   :start-date (or start-date "")
   :end-date (or end-date "")
   :category (or category "")
   :account-id (or account-id "")
   :auto-renew? auto-renew?
   :url (or url "")
   :notes (or notes "")})

(defn- form-field
  "Wraps `input` in a label, adding a modified-field accent when `k`'s
   current value differs from the form's initial value (edit mode only).
   When `accent-color` is given, the accent border uses it directly
   (so it matches the item's category instead of clashing with it);
   otherwise it falls back to the generic `field-modified` color."
  ([modified? k label-text input] (form-field modified? k label-text input nil))
  ([modified? k label-text input accent-color]
   (if (and (modified? k) accent-color)
     [:label {:class "field-modified-accent" :style {:border-left-color accent-color}}
      label-text input]
     [:label {:class (when (modified? k) "field-modified")} label-text input])))

(defn subscription-form
  "Inline form for creating a new subscription, or editing an existing one
   when `:initial` (a subscription) is passed."
  [{:keys [initial on-success on-cancel]}]
  (let [form (r/atom (if initial (subscription->form initial) empty-subscription-form))
        initial-form (when initial (subscription->form initial))
        datalist-id (str "category-options-" (random-uuid))
        confirming-discard? (r/atom false)]
    (fn [{:keys [initial on-success on-cancel]}]
      (let [current @form
            dirty? (and initial-form (not= current initial-form))
            modified? (fn [k] (and initial-form (not= (get current k) (get initial-form k))))
            {form-name :name :keys [cost frequency custom-interval-days
                                     billing-day billing-month billing-weekday start-date end-date
                                     category account-id auto-renew? url notes]} current
            accounts @(re-frame/subscribe [::subs/account-list])
            category-names @(re-frame/subscribe [::subs/category-names])
            category-key (when (seq category) (str/lower-case category))
            category-color (when category-key @(re-frame/subscribe [::subs/category-color category-key]))]
        [:form.entity-form
         {:class (when initial "entity-form--nested")
          :on-submit (fn [e]
                       (.preventDefault e)
                       (if-let [id (:id initial)]
                         (re-frame/dispatch [::events/update-subscription id (form->subscription @form)])
                         (re-frame/dispatch [::events/add-subscription (form->subscription @form)]))
                       (when-not initial (reset! form empty-subscription-form))
                       (when on-success (on-success)))}
         [form-field modified? :name "Name"
          [:input {:type "text" :required true
                   :value form-name
                   :on-change #(swap! form assoc :name (.. % -target -value))}]
          category-color]
         [form-field modified? :cost "Cost"
          [:input {:type "number" :step "0.01" :required true
                   :value cost
                   :on-change #(swap! form assoc :cost (.. % -target -value))}]
          category-color]
         [form-field modified? :frequency "Frequency"
          [:select {:value (name frequency)
                    :on-change #(swap! form assoc :frequency (keyword (.. % -target -value)))}
           (doall
            (for [f [:weekly :monthly :quarterly :yearly :custom]]
              ^{:key f} [:option {:value (name f)} (name f)]))]
          category-color]
         (when (= frequency :custom)
           [form-field modified? :custom-interval-days "Interval (days)"
            [:input {:type "number" :min "1" :step "1" :required true
                     :value custom-interval-days
                     :on-change #(swap! form assoc :custom-interval-days (.. % -target -value))}]
            category-color])
         (when (= frequency :yearly)
           [form-field modified? :billing-month "Billing Month"
            [:select {:value billing-month :required true
                      :on-change #(swap! form assoc :billing-month (.. % -target -value))}
             [:option {:value ""} "Select a month"]
             (doall
              (for [[i month-name] (map-indexed vector month-names)]
                ^{:key i} [:option {:value (str (inc i))} month-name]))]
            category-color])
         (when (contains? #{:monthly :quarterly :yearly} frequency)
           [form-field modified? :billing-day "Billing Day (1-31)"
            [:input {:type "number" :min "1" :max "31" :step "1" :required true
                     :value billing-day
                     :on-change #(swap! form assoc :billing-day (.. % -target -value))}]
            category-color])
         (when (= frequency :weekly)
           [form-field modified? :billing-weekday "Billing Weekday"
            [:select {:value billing-weekday :required true
                      :on-change #(swap! form assoc :billing-weekday (.. % -target -value))}
             [:option {:value ""} "Select a day"]
             (doall
              (for [d [:sunday :monday :tuesday :wednesday :thursday :friday :saturday]]
                ^{:key d} [:option {:value (name d)} (str/capitalize (name d))]))]
            category-color])
         [form-field modified? :start-date "Start Date"
          [:input {:type "date"
                   :value start-date
                   :on-change #(swap! form assoc :start-date (.. % -target -value))}]
          category-color]
         [form-field modified? :end-date "End Date"
          [:input {:type "date"
                   :value end-date
                   :on-change #(swap! form assoc :end-date (.. % -target -value))}]
          category-color]
         [form-field modified? :category "Category"
          [:input {:type "text" :list datalist-id
                   :value category
                   :on-change #(swap! form assoc :category (.. % -target -value))}]
          category-color]
         [:datalist {:id datalist-id}
          (doall
           (for [c category-names]
             ^{:key c} [:option {:value (str/capitalize c)}]))]
         (when category-key
           [:label "Category Color"
            [:input {:type "color"
                     :value (or category-color "#cccccc")
                     :on-change #(re-frame/dispatch
                                  [::events/set-category-color category-key (.. % -target -value)])}]])
         [form-field modified? :account-id "Account"
          [:select {:value account-id
                    :on-change #(swap! form assoc :account-id (.. % -target -value))}
           [:option {:value ""} "No account"]
           (doall
            (for [{:keys [id name last-digits]} accounts]
              ^{:key id} [:option {:value id} (str name " (••" last-digits ")")]))]
          category-color]
         [:label.checkbox-label
          {:class (when (modified? :auto-renew?) (if category-color "field-modified-accent" "field-modified"))
           :style (when (and (modified? :auto-renew?) category-color) {:border-left-color category-color})}
          [:input {:type "checkbox" :checked auto-renew?
                   :on-change #(swap! form assoc :auto-renew? (.. % -target -checked))}]
          " Will Auto-Renew"]
         [form-field modified? :url "Management URL"
          [:input {:type "text"
                   :value url
                   :on-change #(swap! form assoc :url (.. % -target -value))}]
          category-color]
         [form-field modified? :notes "Notes"
          [:textarea {:value notes
                      :on-change #(swap! form assoc :notes (.. % -target -value))}]
          category-color]
         (if @confirming-discard?
           [:div.confirm-prompt
            [:span "Discard unsaved changes?"]
            [:button.danger {:type "button" :on-click #(when on-cancel (on-cancel))} "Discard"]
            [:button {:type "button" :on-click #(reset! confirming-discard? false)} "Keep Editing"]]
           [:div.form-actions
            [:button {:type "submit"} (if initial "Save changes" "Add subscription")]
            (when initial
              [:button {:type "button"
                        :on-click #(if dirty?
                                     (reset! confirming-discard? true)
                                     (when on-cancel (on-cancel)))}
               "Cancel"])])]))))

(defn- filter-controls []
  (let [search @(re-frame/subscribe [::subs/search])
        status-filter @(re-frame/subscribe [::subs/status-filter])
        category-filter @(re-frame/subscribe [::subs/category-filter])
        sort-by @(re-frame/subscribe [::subs/sort-by])
        sort-dir @(re-frame/subscribe [::subs/sort-dir])
        category-names @(re-frame/subscribe [::subs/category-names])]
    [:div.filter-controls
     [:input {:type "search" :placeholder "Search by name..."
              :value search
              :on-change #(re-frame/dispatch [::events/set-search (.. % -target -value)])}]
     [:select {:value (if status-filter (name status-filter) "")
               :on-change #(let [v (.. % -target -value)]
                             (re-frame/dispatch [::events/set-status-filter (when (seq v) (keyword v))]))}
      [:option {:value ""} "All statuses"]
      (doall
       (for [s [:active :paused :cancelled]]
         ^{:key s} [:option {:value (name s)} (str/capitalize (name s))]))]
     [:select {:value (or category-filter "")
               :on-change #(let [v (.. % -target -value)]
                             (re-frame/dispatch [::events/set-category-filter (when (seq v) v)]))}
      [:option {:value ""} "All categories"]
      (doall
       (for [c category-names]
         ^{:key c} [:option {:value c} (str/capitalize c)]))]
     [:select {:value (name sort-by)
               :on-change #(re-frame/dispatch [::events/set-sort-by (keyword (.. % -target -value))])}
      (doall
       (for [[k label] [[:name "Name"] [:cost "Cost"] [:status "Status"]]]
         ^{:key k} [:option {:value (name k)} label]))]
     [:button {:type "button" :on-click #(re-frame/dispatch [::events/toggle-sort-dir])}
      (if (= sort-dir :desc) "↓ Desc" "↑ Asc")]]))

(defn subscriptions-panel []
  (let [show-form? (r/atom false)]
    (fn []
      [:div.subscriptions-panel
       [:div.panel-header
        [:h2 "Subscriptions"]
        [:button {:type "button" :on-click #(swap! show-form? not)}
         (if @show-form? "Cancel" "+ Add subscription")]]
       (when @show-form?
         [subscription-form {:on-success #(reset! show-form? false)}])
       [filter-controls]
       [subscription-list]])))

(defn account-details
  "Full field detail shown when an account card is expanded."
  [{:keys [created-at]}]
  [:div.account-details
   (when created-at
     [:div [:span.field-label "Added: "] created-at])])

(defn account-card
  "A single account; click the summary row to expand/collapse, with
   edit/delete inside the expanded view, mirroring subscription-card."
  [_]
  (let [expanded? (r/atom false)
        editing? (r/atom false)
        confirming? (r/atom false)]
    (fn [{:keys [id name last-digits] :as account}]
      (let [used-by @(re-frame/subscribe [::subs/subscriptions-for-account id])]
        [:div.account-card
         [:div.account-summary {:on-click #(when-not @editing? (swap! expanded? not))
                                 :style {:cursor (if @editing? "default" "pointer")}}
          [:span.account-name name]
          [:span.account-digits (str "••" last-digits)]]
         (when @expanded?
           (cond
             @editing?
             [account-form {:initial account
                            :on-success #(reset! editing? false)
                            :on-cancel #(reset! editing? false)}]

             @confirming?
             [:div.confirm-prompt
              [:span (if (seq used-by)
                       (str "Delete this account? " (count used-by)
                            " subscription" (when (> (count used-by) 1) "s")
                            " using it will be unlinked, not deleted.")
                       "Delete this account?")]
              [:button.danger {:type "button"
                                :on-click #(re-frame/dispatch [::events/remove-account id])}
               "Delete"]
              [:button {:type "button" :on-click #(reset! confirming? false)} "Cancel"]]

             :else
             [:div
              [account-details account]
              [:div.card-actions
               [:button.edit-toggle {:type "button" :on-click #(reset! editing? true)} "Edit"]
               [:button.delete-toggle {:type "button" :on-click #(reset! confirming? true)} "Delete"]]]))]))))

(defn account-list []
  (let [accounts @(re-frame/subscribe [::subs/account-list])]
    [:div.account-list
     (if (empty? accounts)
       [:p.empty-state "No accounts yet."]
       (doall
        (for [{:keys [id] :as account} accounts]
          ^{:key id} [account-card account])))]))

(def ^:private empty-account-form
  {:name "" :last-digits ""})

(defn- account->form
  "Seeds form state from an existing account (both fields are already
   plain strings, so this is mostly identity --- kept for symmetry with
   subscription->form, and in case a future field needs real coercion)."
  [{:keys [name last-digits]}]
  {:name name :last-digits last-digits})

(defn- form->account
  [{:keys [name last-digits]}]
  {:name name :last-digits last-digits})

(defn account-form
  "Inline form for creating a new account, or editing an existing one when
   `:initial` (an account) is passed."
  [{:keys [initial on-success on-cancel]}]
  (let [form (r/atom (if initial (account->form initial) empty-account-form))
        initial-form (when initial (account->form initial))
        confirming-discard? (r/atom false)]
    (fn [{:keys [initial on-success on-cancel]}]
      (let [current @form
            dirty? (and initial-form (not= current initial-form))
            modified? (fn [k] (and initial-form (not= (get current k) (get initial-form k))))
            {:keys [name last-digits]} current]
        [:form.entity-form
         {:class (when initial "entity-form--nested")
          :on-submit (fn [e]
                       (.preventDefault e)
                       (if-let [id (:id initial)]
                         (re-frame/dispatch [::events/update-account id (form->account @form)])
                         (re-frame/dispatch [::events/add-account (form->account @form)]))
                       (when-not initial (reset! form empty-account-form))
                       (when on-success (on-success)))}
         [form-field modified? :name "Name"
          [:input {:type "text" :required true
                   :value name
                   :on-change #(swap! form assoc :name (.. % -target -value))}]]
         [form-field modified? :last-digits "Last Digits"
          [:input {:type "text" :required true
                   :value last-digits
                   :on-change #(swap! form assoc :last-digits (.. % -target -value))}]]
         (if @confirming-discard?
           [:div.confirm-prompt
            [:span "Discard unsaved changes?"]
            [:button.danger {:type "button" :on-click #(when on-cancel (on-cancel))} "Discard"]
            [:button {:type "button" :on-click #(reset! confirming-discard? false)} "Keep Editing"]]
           [:div.form-actions
            [:button {:type "submit"} (if initial "Save changes" "Add account")]
            (when initial
              [:button {:type "button"
                        :on-click #(if dirty?
                                     (reset! confirming-discard? true)
                                     (when on-cancel (on-cancel)))}
               "Cancel"])])]))))

(defn accounts-panel []
  (let [show-form? (r/atom false)]
    (fn []
      [:div.accounts-panel
       [:div.panel-header
        [:h2 "Accounts"]
        [:button {:type "button" :on-click #(swap! show-form? not)}
         (if @show-form? "Cancel" "+ Add account")]]
       (when @show-form?
         [account-form {:on-success #(reset! show-form? false)}])
       [account-list]])))

(defn stats-bar []
  (let [active-count @(re-frame/subscribe [::subs/active-subscription-count])
        monthly @(re-frame/subscribe [::subs/total-monthly-cost])
        yearly @(re-frame/subscribe [::subs/total-yearly-cost])]
    [:div.stats-bar
     [:div.stat [:span.stat-value active-count] [:span.stat-label "Active"]]
     [:div.stat [:span.stat-value (str "$" (.toFixed monthly 2))] [:span.stat-label "Per Month"]]
     [:div.stat [:span.stat-value (str "$" (.toFixed yearly 2))] [:span.stat-label "Per Year"]]]))

(defn- export-data!
  [data]
  (let [blob (js/Blob. #js [(pr-str data)] #js {:type "application/edn"})
        url (js/URL.createObjectURL blob)
        filename (str "subscription-tracker-export-"
                      (.slice (.toISOString (js/Date.)) 0 10) ".edn")
        a (js/document.createElement "a")]
    (set! (.-href a) url)
    (set! (.-download a) filename)
    (.click a)
    (js/URL.revokeObjectURL url)))

(defn- import-file!
  [file]
  (let [reader (js/FileReader.)]
    (set! (.-onload reader)
          (fn [e]
            (re-frame/dispatch [::events/import-data (.. e -target -result)])))
    (.readAsText reader file)))

(defn data-panel []
  (let [export-data @(re-frame/subscribe [::subs/export-data])
        import-error @(re-frame/subscribe [::subs/import-error])]
    [:div.data-panel
     [:div.panel-header
      [:h2 "Data"]]
     [:div.data-actions
      [:button {:type "button" :on-click #(export-data! export-data)} "Export data"]
      [:label.file-input-trigger {:for "import-file-input"} "Import Data"]
      [:input#import-file-input.visually-hidden
       {:type "file" :accept ".edn"
        :on-change (fn [e]
                     (when-let [file (-> e .-target .-files (aget 0))]
                       (import-file! file))
                     (set! (.. e -target -value) ""))}]]
     (when import-error
       [:p.import-error import-error])]))

(defn main-panel []
  [:div
   [:h1 "Subscription Tracker"]
   [stats-bar]
   [subscriptions-panel]
   [accounts-panel]
   [data-panel]])
;; NS: subscription-tracker.views:1 ends here
