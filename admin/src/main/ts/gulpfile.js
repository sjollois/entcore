var gulp = require('gulp')
var rename = require("gulp-rename");

gulp.task("buildWithDockerPath", () => {
    console.log("Copying files to view and public...");
    gulp.src('/home/node/app/dist/**/*')
        .pipe(gulp.dest('/home/node/base/src/main/resources/public/'));
    return gulp.src('/home/node/app/dist/index.html')
        .pipe(rename(function (path) { path.basename = "admin"; }))
        .pipe(gulp.dest('/home/node/base/src/main/resources/view/'));
})

gulp.task("buildWithLocalPath", () => {
    console.log("Copying files to view and public...");
    gulp.src('./dist/**/*')
        .pipe(gulp.dest('../resources/public/'));
    return gulp.src('./dist/index.html')
        .pipe(rename(function (path) { path.basename = "admin"; }))
        .pipe(gulp.dest('../resources/view/'));
})
