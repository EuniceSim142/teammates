package teammates.ui.webapi;

import teammates.common.datatransfer.attributes.CourseAttributes;
import teammates.common.datatransfer.attributes.InstructorAttributes;
import teammates.common.exception.EntityDoesNotExistException;
import teammates.common.exception.InvalidParametersException;
import teammates.common.util.Const;
import teammates.common.util.FieldValidator;
import teammates.storage.sqlentity.Course;
import teammates.ui.output.CourseData;
import teammates.ui.request.CourseUpdateRequest;
import teammates.ui.request.InvalidHttpRequestBodyException;

/**
 * Updates a course.
 */
class UpdateCourseAction extends Action {

    @Override
    AuthType getMinAuthLevel() {
        return AuthType.LOGGED_IN;
    }

    @Override
    void checkSpecificAccessControl() throws UnauthorizedAccessException {
        if (!userInfo.isInstructor) {
            throw new UnauthorizedAccessException("Instructor privilege is required to access this resource.");
        }

        String courseId = getNonNullRequestParamValue(Const.ParamsNames.COURSE_ID);
        InstructorAttributes instructor = logic.getInstructorForGoogleId(courseId, userInfo.id);
        CourseAttributes courseAttributes = logic.getCourse(courseId);

        if (!courseAttributes.isMigrated()) {
            gateKeeper.verifyAccessible(instructor, courseAttributes, Const.InstructorPermissions.CAN_MODIFY_COURSE);
            return;
        }
        Course course = sqlLogic.getCourse(courseId);
        // TODO: Migrate once instructor entity is ready.
        // gateKeeper.verifyAccessible(instructor, course, Const.InstructorPermissions.CAN_MODIFY_COURSE);
    }

    @Override
    public JsonResult execute() throws InvalidHttpRequestBodyException {
        CourseUpdateRequest courseUpdateRequest = getAndValidateRequestBody(CourseUpdateRequest.class);
        String courseTimeZone = courseUpdateRequest.getTimeZone();

        String timeZoneErrorMessage = FieldValidator.getInvalidityInfoForTimeZone(courseTimeZone);
        if (!timeZoneErrorMessage.isEmpty()) {
            throw new InvalidHttpRequestBodyException(timeZoneErrorMessage);
        }

        String courseId = getNonNullRequestParamValue(Const.ParamsNames.COURSE_ID);
        String courseName = courseUpdateRequest.getCourseName();

        try {
            CourseAttributes courseAttributes = logic.getCourse(courseId);
            if (courseAttributes != null && !courseAttributes.isMigrated()) {
                return updateWithDatastore(courseId, courseName, courseTimeZone);
            } 

            Course updatedCourse = sqlLogic.updateCourseCascade(
                new Course(courseId, courseName, courseTimeZone, null));
            return new JsonResult(new CourseData(updatedCourse));
            
        } catch (InvalidParametersException ipe) {
            throw new InvalidHttpRequestBodyException(ipe);
        } catch (EntityDoesNotExistException edee) {
            throw new EntityNotFoundException(edee);
        }
    }

    private JsonResult updateWithDatastore(String courseId, String courseName, String courseTimeZone) throws InvalidParametersException, EntityDoesNotExistException {
        CourseAttributes updatedCourseAttributes = logic.updateCourseCascade(
            CourseAttributes.updateOptionsBuilder(courseId)
                    .withName(courseName)
                    .withTimezone(courseTimeZone)
                    .build());
        return new JsonResult(new CourseData(updatedCourseAttributes));
    }
}
