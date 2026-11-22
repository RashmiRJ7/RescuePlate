package com.foodrescue.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "food-rescue")
public class ScoringProperties {

    private Urgency urgency = new Urgency();
    private Distance distance = new Distance();

    public Urgency getUrgency() {
        return urgency;
    }

    public void setUrgency(Urgency urgency) {
        this.urgency = urgency;
    }

    public Distance getDistance() {
        return distance;
    }

    public void setDistance(Distance distance) {
        this.distance = distance;
    }

    public static class Urgency {
        private double weightTime = 0.60;
        private double weightPerishability = 0.30;
        private double weightQuantity = 0.10;
        private long timeFullUrgencyMinutes = 30;
        private long timeZeroUrgencyMinutes = 360;
        private int quantityCap = 100;

        public double getWeightTime() {
            return weightTime;
        }

        public void setWeightTime(double weightTime) {
            this.weightTime = weightTime;
        }

        public double getWeightPerishability() {
            return weightPerishability;
        }

        public void setWeightPerishability(double weightPerishability) {
            this.weightPerishability = weightPerishability;
        }

        public double getWeightQuantity() {
            return weightQuantity;
        }

        public void setWeightQuantity(double weightQuantity) {
            this.weightQuantity = weightQuantity;
        }

        public long getTimeFullUrgencyMinutes() {
            return timeFullUrgencyMinutes;
        }

        public void setTimeFullUrgencyMinutes(long timeFullUrgencyMinutes) {
            this.timeFullUrgencyMinutes = timeFullUrgencyMinutes;
        }

        public long getTimeZeroUrgencyMinutes() {
            return timeZeroUrgencyMinutes;
        }

        public void setTimeZeroUrgencyMinutes(long timeZeroUrgencyMinutes) {
            this.timeZeroUrgencyMinutes = timeZeroUrgencyMinutes;
        }

        public int getQuantityCap() {
            return quantityCap;
        }

        public void setQuantityCap(int quantityCap) {
            this.quantityCap = quantityCap;
        }
    }

    public static class Distance {
        private double earthRadiusKm = 6371.0;
        private int travelMinutesPerKm = 3;

        public double getEarthRadiusKm() {
            return earthRadiusKm;
        }

        public void setEarthRadiusKm(double earthRadiusKm) {
            this.earthRadiusKm = earthRadiusKm;
        }

        public int getTravelMinutesPerKm() {
            return travelMinutesPerKm;
        }

        public void setTravelMinutesPerKm(int travelMinutesPerKm) {
            this.travelMinutesPerKm = travelMinutesPerKm;
        }
    }
}
