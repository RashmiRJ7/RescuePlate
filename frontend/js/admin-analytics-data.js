(function (root) {
  "use strict";

  const monthlyDonationCounts = [8, 10, 12, 14, 11, 16, 18, 17, 20, 22, 24, 26, 28];
  const statuses = ["Available", "Reserved", "Picked Up", "Delivered", "Expired"];
  const ngoPartners = [
    "Annadaan Community Trust",
    "Sahyog Food Collective",
    "Seva Meals Network",
    "Uday Foundation Pune",
    "Aahar Seva Trust"
  ];
  const donorProfiles = [
    { donorType: "Hostel/Mess", donor: "Shivaji College Hostel Mess", foodCategory: "Cooked Meals", mealCount: 40 },
    { donorType: "Hostel/Mess", donor: "Green Valley Hostel Mess", foodCategory: "Cooked Meals", mealCount: 60 },
    { donorType: "Hostel/Mess", donor: "Prerna Student Residence", foodCategory: "Cooked Meals", mealCount: 35 },
    { donorType: "Hotel", donor: "Deccan Heritage Hotel", foodCategory: "Cooked Meals", mealCount: 35 },
    { donorType: "Hotel", donor: "Saffron Court Hotel", foodCategory: "Cooked Meals", mealCount: 55 },
    { donorType: "Restaurant", donor: "Annapurna Kitchen", foodCategory: "Cooked Meals", mealCount: 45 },
    { donorType: "Restaurant", donor: "Pune Spice House", foodCategory: "Cooked Meals", mealCount: 50 },
    { donorType: "Restaurant", donor: "Aangan Restaurant", foodCategory: "Fresh Produce", mealCount: 30 },
    { donorType: "Bakery", donor: "Asha Bakery & Sweets", foodCategory: "Bakery & Sweets", mealCount: 25 },
    { donorType: "Bakery", donor: "Daily Bread Pune", foodCategory: "Bakery & Sweets", mealCount: 30 },
    { donorType: "Bakery", donor: "Meetha Ghar Bakery", foodCategory: "Bakery & Sweets", mealCount: 28 },
    { donorType: "Events/Catering", donor: "Saanvi Wedding Caterers", foodCategory: "Event Meals", mealCount: 120 },
    { donorType: "Events/Catering", donor: "Campus Utsav Team", foodCategory: "Packaged Snacks", mealCount: 50 },
    { donorType: "Events/Catering", donor: "Shubh Events Catering", foodCategory: "Event Meals", mealCount: 90 },
    { donorType: "Other", donor: "Community Kitchen Pune", foodCategory: "Cooked Meals", mealCount: 32 },
    { donorType: "Other", donor: "Market Fresh Collective", foodCategory: "Fresh Produce", mealCount: 0 }
  ];

  function createDemoDonations() {
    const now = new Date();
    const records = [];

    monthlyDonationCounts.forEach(function (count, monthIndex) {
      const monthsAgo = monthlyDonationCounts.length - monthIndex - 1;
      const monthStart = new Date(now.getFullYear(), now.getMonth() - monthsAgo, 1);
      const daysInMonth = new Date(monthStart.getFullYear(), monthStart.getMonth() + 1, 0).getDate();
      const lastAvailableDay = monthsAgo === 0 ? now.getDate() : daysInMonth;

      for (let itemIndex = 0; itemIndex < count; itemIndex += 1) {
        const profile = donorProfiles[(monthIndex * 3 + itemIndex * 5) % donorProfiles.length];
        const status = statuses[(itemIndex + monthIndex * 2) % statuses.length];
        const day = 1 + ((itemIndex * 7 + monthIndex * 3) % lastAvailableDay);
        const createdAt = new Date(
          monthStart.getFullYear(),
          monthStart.getMonth(),
          day,
          8 + ((itemIndex + monthIndex) % 12)
        );
        const mealCount = profile.mealCount === 0 ? 0 : profile.mealCount + (itemIndex % 4) * 5;
        const needsNgo = status === "Reserved" || status === "Picked Up" || status === "Delivered";

        records.push({
          id: "analytics-" + monthIndex + "-" + itemIndex,
          createdAt: createdAt,
          donorType: profile.donorType,
          donor: profile.donor,
          foodCategory: profile.foodCategory,
          status: status,
          ngo: needsNgo ? ngoPartners[(monthIndex + itemIndex) % ngoPartners.length] : "",
          mealCount: mealCount,
          mealsRescued: status === "Delivered" ? mealCount : 0
        });
      }
    });

    return records;
  }

  const demoDonations = createDemoDonations();

  root.RescuePlateAdminAnalyticsData = {
    getDonations: function () {
      return demoDonations.slice();
    }
  };
}(window));
