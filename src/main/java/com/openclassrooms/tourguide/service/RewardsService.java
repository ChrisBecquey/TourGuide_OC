package com.openclassrooms.tourguide.service;

import com.openclassrooms.tourguide.user.User;
import com.openclassrooms.tourguide.user.UserReward;
import gpsUtil.GpsUtil;
import gpsUtil.location.Attraction;
import gpsUtil.location.Location;
import gpsUtil.location.VisitedLocation;
import org.springframework.stereotype.Service;
import rewardCentral.RewardCentral;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class RewardsService {
    private static final double STATUTE_MILES_PER_NAUTICAL_MILE = 1.15077945;

    // proximity in miles
    private int defaultProximityBuffer = 10;
    private int proximityBuffer = defaultProximityBuffer;
    private int attractionProximityRange = 200;
    private final GpsUtil gpsUtil;
    private final RewardCentral rewardsCentral;


    private int threads = Runtime.getRuntime().availableProcessors();

    public RewardsService(GpsUtil gpsUtil, RewardCentral rewardCentral) {
        this.gpsUtil = gpsUtil;
        this.rewardsCentral = rewardCentral;
    }

    public void setProximityBuffer(int proximityBuffer) {
        this.proximityBuffer = proximityBuffer;
    }

    public void setDefaultProximityBuffer() {
        proximityBuffer = defaultProximityBuffer;
    }

    public void calculateRewards(User user) {
        List<VisitedLocation> userLocations = new ArrayList<>(user.getVisitedLocations());
        List<Attraction> attractions = gpsUtil.getAttractions();

        List<UserReward> rewards = new ArrayList<>();

        userLocations.stream()
                .flatMap(visitedLocation ->
                        attractions.stream()
                                .filter(attraction -> nearAttraction(visitedLocation, attraction))
                                .filter(attraction -> rewards.stream()
                                        .noneMatch(r -> r.attraction.attractionName.equals(attraction.attractionName)))
                                .map(attraction ->
                                        new UserReward(visitedLocation, attraction, getRewardPoints(attraction, user))
                                )
                )
                .forEach(rewards::add);

        user.setUserRewards(rewards);
    }

    public void calculateRewardsAsync(List<User> users) {
        // Création d'un executorService avec le nombre de thread souhaité
        ExecutorService executorService = Executors.newFixedThreadPool(50);

        // countDown avec un nombre = aux nombre d'utilisateurs
        CountDownLatch countDownLatch = new CountDownLatch(users.size());

        // TODO passez en stream pour cohérence avec le projet
        // Soumet des taches à chaque utilisateur !
        for(User user : users) {
            executorService.submit(() -> {
                calculateRewards(user);
                countDownLatch.countDown(); // on descent de countdown
            });
        }

        try{
            countDownLatch.await();
        } catch(InterruptedException e) {
            e.printStackTrace();
        }

        executorService.shutdown();

    }

    public boolean isWithinAttractionProximity(Attraction attraction, Location location) {
        return getDistance(attraction, location) > attractionProximityRange ? false : true;
    }

    private boolean nearAttraction(VisitedLocation visitedLocation, Attraction attraction) {
        return getDistance(attraction, visitedLocation.location) > proximityBuffer ? false : true;
    }

    public int getRewardPoints(Attraction attraction, User user) {
        return rewardsCentral.getAttractionRewardPoints(attraction.attractionId, user.getUserId());
    }

    public double getDistance(Location loc1, Location loc2) {
        double lat1 = Math.toRadians(loc1.latitude);
        double lon1 = Math.toRadians(loc1.longitude);
        double lat2 = Math.toRadians(loc2.latitude);
        double lon2 = Math.toRadians(loc2.longitude);

        double angle = Math.acos(Math.sin(lat1) * Math.sin(lat2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.cos(lon1 - lon2));

        double nauticalMiles = 60 * Math.toDegrees(angle);
        double statuteMiles = STATUTE_MILES_PER_NAUTICAL_MILE * nauticalMiles;
        return statuteMiles;
    }

}
