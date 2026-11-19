import { referenceService } from './referenceService'
export const aircraftTypeService = { list: referenceService.aircraftTypes, get: referenceService.aircraftType, qualifications: referenceService.qualificationsForType }
