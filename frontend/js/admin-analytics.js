(function () {
  "use strict";

  const dateRangeFilter = document.getElementById("analytics-date-range");
  const donorTypeFilter = document.getElementById("analytics-donor-type");
  const statusFilter = document.getElementById("analytics-status");
  const chartError = document.getElementById("analytics-chart-error");
  const charts = {};
  const numberFormat = new Intl.NumberFormat("en-IN");
  const dateFormat = new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    day: "numeric",
    month: "short",
    year: "numeric"
  });
  const monthFormat = new Intl.DateTimeFormat("en-IN", {
    timeZone: "Asia/Kolkata",
    month: "short",
    year: "2-digit"
  });
  const donorTypes = ["Hostel/Mess", "Hotel", "Restaurant", "Bakery", "Events/Catering", "Other"];
  const donationStatuses = ["Available", "Reserved", "Picked Up", "Delivered", "Expired"];
  const foodCategories = ["Cooked Meals", "Bakery & Sweets", "Event Meals", "Packaged Snacks", "Fresh Produce"];
  const colors = {
    forest: "#1f6b4a",
    deepForest: "#143d2e",
    greenSoft: "#87a98e",
    orange: "#c56b2d",
    saffron: "#e5aa54",
    turmeric: "#d8bf63",
    sage: "#a9bd9e",
    cream: "#e9e1d3",
    muted: "#879286"
  };
  const donorTypeColors = [
    colors.forest,
    colors.orange,
    colors.saffron,
    colors.greenSoft,
    colors.turmeric,
    colors.cream
  ];
  const statusColors = [
    colors.forest,
    colors.saffron,
    colors.greenSoft,
    colors.deepForest,
    colors.muted
  ];
  const categoryColors = [
    colors.forest,
    colors.orange,
    colors.saffron,
    colors.greenSoft,
    colors.turmeric
  ];

  function showError(message) {
    chartError.textContent = message;
    chartError.hidden = false;
  }

  if (!window.Chart) {
    showError("Analytics charts could not load. Check your internet connection and reload this page.");
    return;
  }

  window.Chart.defaults.color = "#6d776e";
  window.Chart.defaults.font.family = "Manrope, sans-serif";
  window.Chart.defaults.font.size = 10;

  const dataSource = window.RescuePlateAdminAnalyticsData;
  if (!dataSource || typeof dataSource.getDonations !== "function") {
    showError("Analytics demo data is unavailable. Reload the page to try again.");
    return;
  }

  const records = dataSource.getDonations();

  function formatNumber(value) {
    return numberFormat.format(value);
  }

  function monthKey(date) {
    return date.getFullYear() + "-" + String(date.getMonth() + 1).padStart(2, "0");
  }

  function getMonthBuckets(start, end) {
    const buckets = [];
    const cursor = new Date(start.getFullYear(), start.getMonth(), 1);
    const lastMonth = new Date(end.getFullYear(), end.getMonth(), 1);

    while (cursor <= lastMonth) {
      const monthDate = new Date(cursor.getFullYear(), cursor.getMonth(), 1);
      buckets.push({
        key: monthKey(monthDate),
        label: monthFormat.format(monthDate),
        donations: 0,
        mealsRescued: 0
      });
      cursor.setMonth(cursor.getMonth() + 1);
    }

    return buckets;
  }

  function getFilteredData() {
    const end = new Date();
    const start = new Date(end.getTime() - Number(dateRangeFilter.value) * 24 * 60 * 60 * 1000);
    const selectedDonorType = donorTypeFilter.value;
    const selectedStatus = statusFilter.value;
    const filteredRecords = records.filter(function (record) {
      const createdAt = new Date(record.createdAt);
      return !Number.isNaN(createdAt.getTime()) &&
        createdAt >= start &&
        createdAt <= end &&
        (selectedDonorType === "all" || record.donorType === selectedDonorType) &&
        (selectedStatus === "all" || record.status === selectedStatus);
    });

    return { start: start, end: end, records: filteredRecords };
  }

  function countBy(recordsToCount, property, categories) {
    return categories.map(function (category) {
      return recordsToCount.filter(function (record) {
        return record[property] === category;
      }).length;
    });
  }

  function sumMeals(recordsToCount) {
    return recordsToCount.reduce(function (total, record) {
      return total + (Number(record.mealsRescued) || 0);
    }, 0);
  }

  function createChartOptions(tooltipLabel, scales) {
    return {
      responsive: true,
      maintainAspectRatio: false,
      animation: { duration: 300 },
      interaction: { intersect: false, mode: "index" },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: {
            label: function (context) {
              return tooltipLabel(context);
            }
          }
        }
      },
      scales: scales
    };
  }

  function createCountScale() {
    return {
      beginAtZero: true,
      ticks: {
        precision: 0,
        callback: function (value) {
          return formatNumber(value);
        }
      },
      grid: { color: "rgba(20, 61, 46, 0.08)" }
    };
  }

  function renderChart(key, canvasId, type, labels, dataset, options) {
    const chartData = { labels: labels, datasets: [dataset] };
    if (charts[key]) {
      charts[key].data = chartData;
      charts[key].options = options;
      charts[key].update();
      return;
    }

    const canvas = document.getElementById(canvasId);
    if (!canvas) {
      showError("An analytics chart container is missing. Reload the page to try again.");
      return;
    }
    charts[key] = new window.Chart(canvas, { type: type, data: chartData, options: options });
  }

  function updateMonthlyCharts(filteredRecords, start, end) {
    const buckets = getMonthBuckets(start, end);
    const bucketByKey = new Map(buckets.map(function (bucket) {
      return [bucket.key, bucket];
    }));

    filteredRecords.forEach(function (record) {
      const bucket = bucketByKey.get(monthKey(new Date(record.createdAt)));
      if (!bucket) return;
      bucket.donations += 1;
      bucket.mealsRescued += Number(record.mealsRescued) || 0;
    });

    renderChart(
      "monthlyDonations",
      "monthly-donations-chart",
      "bar",
      buckets.map(function (bucket) { return bucket.label; }),
      {
        label: "Donations",
        data: buckets.map(function (bucket) { return bucket.donations; }),
        backgroundColor: colors.forest,
        hoverBackgroundColor: colors.deepForest,
        borderRadius: 5,
        maxBarThickness: 34
      },
      createCountScaleOptions(function (context) {
        return " " + formatNumber(context.parsed.y) + " donations";
      })
    );

    renderChart(
      "mealsTrend",
      "meals-trend-chart",
      "line",
      buckets.map(function (bucket) { return bucket.label; }),
      {
        label: "Meals rescued",
        data: buckets.map(function (bucket) { return bucket.mealsRescued; }),
        borderColor: colors.orange,
        backgroundColor: "rgba(197, 107, 45, 0.12)",
        pointBackgroundColor: colors.orange,
        pointBorderColor: "#fffdf9",
        pointBorderWidth: 2,
        pointRadius: 4,
        pointHoverRadius: 6,
        tension: 0.36,
        fill: true
      },
      createCountScaleOptions(function (context) {
        return " " + formatNumber(context.parsed.y) + " meals rescued";
      })
    );

    return buckets;
  }

  function createCountScaleOptions(tooltipLabel) {
    return createChartOptions(tooltipLabel, {
      x: {
        grid: { display: false },
        ticks: { maxRotation: 0, autoSkip: true }
      },
      y: createCountScale()
    });
  }

  function updateDonorTypeChart(filteredRecords) {
    const values = countBy(filteredRecords, "donorType", donorTypes);
    renderChart(
      "donorTypes",
      "donor-type-chart",
      "doughnut",
      donorTypes,
      {
        label: "Donations",
        data: values,
        backgroundColor: donorTypeColors,
        borderColor: "#fffdf9",
        borderWidth: 3,
        hoverOffset: 5
      },
      {
        responsive: true,
        maintainAspectRatio: false,
        cutout: "64%",
        plugins: {
          legend: {
            display: true,
            position: "bottom",
            labels: { usePointStyle: true, pointStyle: "circle", padding: 14, boxWidth: 8, font: { size: 10 } }
          },
          tooltip: {
            callbacks: {
              label: function (context) {
                return " " + context.label + ": " + formatNumber(context.parsed) + " donations";
              }
            }
          }
        }
      }
    );
  }

  function updateStatusChart(filteredRecords) {
    const values = countBy(filteredRecords, "status", donationStatuses);
    renderChart(
      "donationStatus",
      "donation-status-chart",
      "bar",
      donationStatuses,
      {
        label: "Donations",
        data: values,
        backgroundColor: statusColors,
        borderRadius: 5,
        maxBarThickness: 38
      },
      createCountScaleOptions(function (context) {
        return " " + formatNumber(context.parsed.y) + " donations";
      })
    );
  }

  function updateFoodCategoryChart(filteredRecords) {
    const values = countBy(filteredRecords, "foodCategory", foodCategories);
    renderChart(
      "foodCategories",
      "food-category-chart",
      "bar",
      foodCategories,
      {
        label: "Donations",
        data: values,
        backgroundColor: categoryColors,
        borderRadius: 5,
        maxBarThickness: 26
      },
      {
        ...createChartOptions(function (context) {
          return " " + formatNumber(context.parsed.x) + " donations";
        }, {
          x: {
            ...createCountScale(),
            grid: { color: "rgba(20, 61, 46, 0.08)" }
          },
          y: { grid: { display: false } }
        }),
        indexAxis: "y"
      }
    );
  }

  function setText(id, value) {
    document.getElementById(id).textContent = value;
  }

  function updateInsights(filteredRecords, buckets, rescuedMeals) {
    const donorCounts = countBy(filteredRecords, "donorType", donorTypes);
    const topDonorIndex = donorCounts.indexOf(Math.max.apply(null, donorCounts));
    const topMonth = buckets.reduce(function (best, bucket) {
      return !best || bucket.donations > best.donations ? bucket : best;
    }, null);
    const statusCounts = countBy(filteredRecords, "status", donationStatuses);
    const topStatusIndex = statusCounts.indexOf(Math.max.apply(null, statusCounts));

    if (filteredRecords.length === 0) {
      setText("insight-top-donor", "No matching donations");
      setText("insight-top-donor-count", "Adjust the filters to see donor activity");
      setText("insight-top-month", "No matching donations");
      setText("insight-top-month-count", "Adjust the filters to see monthly activity");
      setText("insight-top-status", "No matching donations");
      setText("insight-top-status-count", "Adjust the filters to see status counts");
    } else {
      setText("insight-top-donor", donorTypes[topDonorIndex]);
      setText("insight-top-donor-count", formatNumber(donorCounts[topDonorIndex]) + " donations");
      setText("insight-top-month", topMonth.label);
      setText("insight-top-month-count", formatNumber(topMonth.donations) + " donations");
      setText("insight-top-status", donationStatuses[topStatusIndex]);
      setText("insight-top-status-count", formatNumber(statusCounts[topStatusIndex]) + " donations");
    }

    setText("insight-rescued-meals", formatNumber(rescuedMeals));
  }

  function refreshAnalytics() {
    const filteredData = getFilteredData();
    const filteredRecords = filteredData.records;
    const rescuedMeals = sumMeals(filteredRecords);
    const buckets = updateMonthlyCharts(filteredRecords, filteredData.start, filteredData.end);
    const activeDonors = new Set(filteredRecords.map(function (record) { return record.donor; }).filter(Boolean)).size;
    const registeredNgos = new Set(filteredRecords.map(function (record) { return record.ngo; }).filter(Boolean)).size;

    setText("analytics-record-count", formatNumber(filteredRecords.length));
    setText(
      "analytics-date-summary",
      dateFormat.format(filteredData.start) + " – " + dateFormat.format(filteredData.end)
    );
    setText("analytics-total-donations", formatNumber(filteredRecords.length));
    setText("analytics-total-meals", formatNumber(rescuedMeals));
    setText("analytics-active-donors", formatNumber(activeDonors));
    setText("analytics-registered-ngos", formatNumber(registeredNgos));

    updateDonorTypeChart(filteredRecords);
    updateStatusChart(filteredRecords);
    updateFoodCategoryChart(filteredRecords);
    updateInsights(filteredRecords, buckets, rescuedMeals);
  }

  dateRangeFilter.addEventListener("change", refreshAnalytics);
  donorTypeFilter.addEventListener("change", refreshAnalytics);
  statusFilter.addEventListener("change", refreshAnalytics);
  document.getElementById("analytics-reset").addEventListener("click", function () {
    dateRangeFilter.value = "180";
    donorTypeFilter.value = "all";
    statusFilter.value = "all";
    refreshAnalytics();
  });

  window.addEventListener("pagehide", function () {
    Object.keys(charts).forEach(function (key) {
      charts[key].destroy();
      delete charts[key];
    });
  });

  refreshAnalytics();
}());
