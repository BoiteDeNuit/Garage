package com.example.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "listings")
public class Listing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "seller_id", nullable = false)
    private AppUser seller;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ListingStatus status;
    @Column(nullable = false)
    private String brand;
    @Column(nullable = false)
    private String model;
    private String engineCode;
    private int horsePower;
    private int year;
    private Integer mileageKm;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FuelType fuelType;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private Transmission transmission;
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private BodyType bodyType;
    @Column(precision = 12,scale = 2)
    private BigDecimal price;
    @Column(length = 100)
    private String city;
    @Column(length = 2000)
    private String description;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant publishedAt;
    private Instant submittedAt;
    @Column(length = 500)
    private String rejectionReason;
    // Кто из модераторов взял объявление в работу. Пишет их только условный UPDATE (ListingRepository.claim)
    @Column(name = "moderator_id")
    private Long moderatorId;
    private Instant claimedAt;
    @Version
    private Long version;
    protected Listing() {}
    // Время приходит снаружи из Clock, а не через @CreationTimestamp: так тесты на Clock.fixed детерминированы
    public static Listing draft(AppUser seller, ListingDetails details, Instant now)
    {
        Listing listing = new Listing();
        listing.seller=seller;
        listing.status=ListingStatus.DRAFT;
        listing.createdAt=now;
        listing.updatedAt=now;
        listing.apply(details);
        return listing;
    }
    // Сначала все проверки, потом изменения: при отказе объект остаётся как был.
    // С модерацией объявление уходит на проверку, опубликует его модератор (approve).
    // То, что уже ждёт проверки, продавец сам не публикует, даже если модерацию тем временем выключили
    public void publish(Instant now, boolean reviewRequired)
    {
        if(status == ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.underReview();
        }
        ListingStatus target = reviewRequired ? ListingStatus.PENDING_REVIEW : ListingStatus.ACTIVE;
        checkTransition(target);
        if(price == null)
        {
            throw ListingStateException.priceRequired();
        }
        if(city == null || city.isBlank())
        {
            throw ListingStateException.cityRequired();
        }
        this.status=target;
        this.rejectionReason=null;
        this.updatedAt=now;
        if(reviewRequired)
        {
            this.submittedAt=now;
        }
        else
        {
            this.publishedAt=now;
        }
    }
    // Решение модератора. Одобрить можно только то, что ждёт проверки: черновик в обход очереди не опубликовать
    public void approve(Instant now)
    {
        if(status != ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.notUnderReview();
        }
        this.status=ListingStatus.ACTIVE;
        this.publishedAt=now;
        this.updatedAt=now;
        releaseClaim();
    }
    // Причину видит продавец: исправит и отправит снова
    public void reject(String reason, Instant now)
    {
        if(status != ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.notUnderReview();
        }
        this.status=ListingStatus.REJECTED;
        this.rejectionReason=reason;
        this.updatedAt=now;
        releaseClaim();
    }
    public boolean isClaimedBy(Long moderator)
    {
        return moderator.equals(moderatorId);
    }
    private void releaseClaim()
    {
        this.moderatorId=null;
        this.claimedAt=null;
    }
    public void markSold(Instant now)
    {
        checkTransition(ListingStatus.SOLD);
        this.status=ListingStatus.SOLD;
        this.updatedAt=now;
    }
    public void archive(Instant now)
    {
        checkTransition(ListingStatus.ARCHIVED);
        this.status=ListingStatus.ARCHIVED;
        this.updatedAt=now;
    }
    // Правка описания. Статус и продавец не меняются: для статуса есть свои действия, продавец — это владелец
    public void updateDetails(ListingDetails details, Instant now)
    {
        checkEditable();
        boolean losesPriceOrCity = details.price() == null || details.city() == null || details.city().isBlank();
        if(status == ListingStatus.ACTIVE && losesPriceOrCity)
        {
            throw ListingStateException.activeNeedsPriceAndCity();
        }
        apply(details);
        this.updatedAt=now;
    }
    // Проданное не меняется: ни описание, ни фото. То, что на проверке, тоже: модератор смотрит конкретную версию
    public void checkEditable()
    {
        if(status == ListingStatus.SOLD)
        {
            throw ListingStateException.soldIsFinal();
        }
        if(status == ListingStatus.PENDING_REVIEW)
        {
            throw ListingStateException.underReview();
        }
    }
    // Удаляется только то, чего никто, кроме продавца и модератора, не видел: черновик, ждущее проверки
    // и отклонённое, если их ни разу не публиковали. Опубликованное снимают в архив, история остаётся
    public void checkDeletable()
    {
        boolean neverShown = publishedAt == null
                && (status == ListingStatus.DRAFT || status == ListingStatus.PENDING_REVIEW || status == ListingStatus.REJECTED);
        if(!neverShown)
        {
            throw ListingStateException.notDraft();
        }
    }
    private void checkTransition(ListingStatus target)
    {
        if(!status.canTransitionTo(target))
        {
            throw ListingStateException.transition(status, target);
        }
    }
    private void apply(ListingDetails details)
    {
        this.brand=details.brand();
        this.model=details.model();
        this.engineCode=details.engineCode();
        this.horsePower=details.horsePower();
        this.year=details.year();
        this.mileageKm=details.mileageKm();
        this.fuelType=details.fuelType();
        this.transmission=details.transmission();
        this.bodyType=details.bodyType();
        // Масштаб как у колонки numeric(12,2): ответ на POST совпадает с тем, что потом отдаст GET.
        // Больше двух знаков после запятой сюда не доходит, это отсекает @Digits в запросе
        this.price=details.price() == null ? null : details.price().setScale(2);
        this.city=details.city();
        this.description=details.description();
    }

    public Long getId() {
        return id;
    }

    public AppUser getSeller() {
        return seller;
    }

    public ListingStatus getStatus() {
        return status;
    }

    public String getBrand() {
        return brand;
    }

    public String getModel() {
        return model;
    }

    public String getEngineCode() {
        return engineCode;
    }

    public int getHorsePower() {
        return horsePower;
    }

    public int getYear() {
        return year;
    }

    public Integer getMileageKm() {
        return mileageKm;
    }

    public FuelType getFuelType() {
        return fuelType;
    }

    public Transmission getTransmission() {
        return transmission;
    }

    public BodyType getBodyType() {
        return bodyType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCity() {
        return city;
    }

    public String getDescription() {
        return description;
    }

    public Instant getSubmittedAt()
    {
        return submittedAt;
    }

    public String getRejectionReason()
    {
        return rejectionReason;
    }

    public Long getModeratorId()
    {
        return moderatorId;
    }

    public Instant getClaimedAt()
    {
        return claimedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Long getVersion() {
        return version;
    }
    // Равенство по id: одна строка, загруженная в двух persistence context, — одно объявление.
    // Новое без id равно только самому себе. instanceof, а не сравнение классов: прокси Hibernate —
    // наследник Listing. Hibernate.getClass ради класса грузит прокси select'ом, а getClassLazy
    // падает на прокси без сессии. Своих наследников у Listing нет, поэтому instanceof тут точный
    @Override
    public boolean equals(Object o)
    {
        if(this == o)
        {
            return true;
        }
        if(!(o instanceof Listing other))
        {
            return false;
        }
        // getId(), а не other.id: поля у прокси пустые, id он отдаёт только через геттер
        return id != null && id.equals(other.getId());
    }
    // Хэш один на класс: id появляется только при persist, хэш по id потерял бы объявление в HashSet.
    // У прокси хэш тот же: Hibernate передаёт вызов настоящему объекту
    @Override
    public int hashCode()
    {
        return Listing.class.hashCode();
    }
    // Без seller: иначе toString дёрнет ленивую загрузку продавца
    @Override
    public String toString()
    {
        return "Объявление {id=" + id + ", " + brand + " " + model + " " + year + "г, " + status + "}";
    }
}
